package com.classroom.rfdetector

import android.content.ContextWrapper
import android.os.Looper
import com.classroom.rfdetector.core.CheckpointStore
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.SignalPresence
import java.io.File
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CapturePersistenceTest {
    @get:Rule val directory = TemporaryFolder()
    private lateinit var vm: AppViewModel
    private val context get() = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getFilesDir() = directory.root
    }
    private val disk get() = CheckpointStore(File(directory.root, "checkpoints"))
    private val briefAddress = "00:11:22:33:44:55"

    @Before fun create() {
        vm = AppViewModel(context)
        awaitInitialStorage()
        vm.setSim(true)
        vm.updateAreaDraft("A 시험실")
    }

    @After fun close() { vm.shutdown() }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        fail(message)
    }

    private fun awaitInitialStorage() = awaitCondition("Initial stores did not load") { !vm.snapshotBusy.value }

    private fun awaitCheckpointCount(count: Int) {
        awaitCondition("Expected $count checkpoint sessions") { !vm.checkpointBusy.value && vm.checkpoints.value.size == count }
        assertNull(vm.checkpointError.value)
    }

    private fun addBriefHistoricalPacket(): Long {
        val captured = System.currentTimeMillis() - 20_000
        // A packet fixture already beyond BLE's quiet interval; no wall-clock sleep is needed.
        vm.scanSession.value = vm.scanSession.value!!.copy(startedAtMs = captured - 1_000)
        vm.tracker.update(RfObservation(briefAddress, ScanSource.BLE, "AirPods Pro", -65, null, false, captured))
        return captured
    }

    @Test fun stopPreservesBriefHistoryWithoutBaselineOrManualSnapshot() {
        vm.startScan()
        val captured = addBriefHistoricalPacket()
        vm.stopScan()
        awaitCheckpointCount(1)
        val restored = disk.load().single().signals.single { it.address == briefAddress }
        assertEquals(SignalPresence.HISTORY, restored.presence)
        assertEquals(captured, restored.firstSeenMs)
        assertEquals(captured, restored.lastSeenMs)
        assertEquals(1, restored.observationCount)
        assertEquals(1, restored.periods.single().count)
        assertTrue(vm.savedSnapshots.value.isEmpty())
        assertNull(vm.baselineSnapshot.value)
        assertFalse(vm.baselineSet.value)
    }

    @Test fun baselineIncludesEarlierBriefPacketSoItsReturnIsNotNewEquipment() {
        vm.startScan()
        val captured = addBriefHistoricalPacket()
        vm.saveSnapshot("A 시험실", "입실 전", asBaseline = true)
        awaitCondition("Baseline did not save") { !vm.snapshotBusy.value }
        assertNull(vm.snapshotError.value)
        val baselineSignal = vm.baselineSnapshot.value!!.signals.single { it.address == briefAddress }
        assertEquals(SignalPresence.HISTORY, baselineSignal.presence)
        assertEquals(captured, baselineSignal.lastSeenMs)
        assertTrue(vm.baselineSet.value)

        vm.tracker.update(RfObservation(briefAddress, ScanSource.BLE, "AirPods Pro", -60, null, false, System.currentTimeMillis()))
        vm.stopScan() // Refreshes the public device list after the returning packet.
        awaitCheckpointCount(1)
        val returned = vm.devices.value.single { it.key == briefAddress }
        assertEquals(SignalPresence.RECENT, returned.presence)
        assertEquals(2, returned.periods.size)
        assertFalse("An intermittent device observed before entry is already part of the baseline", returned.isBaselineNew)
        assertEquals(0, vm.newCount.value)
        assertEquals(captured, vm.baselineSnapshot.value!!.signals.single { it.address == briefAddress }.lastSeenMs)
    }

    @Test fun fiveSecondCheckpointIncludesVanishedSignalsWhileScanKeepsRunning() {
        vm.startScan()
        addBriefHistoricalPacket()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        awaitCheckpointCount(1)
        assertTrue(vm.scanning.value)
        val saved = disk.load().single()
        assertEquals(6, saved.signals.size)
        assertEquals(SignalPresence.HISTORY, saved.signals.single { it.address == briefAddress }.presence)
        assertTrue(vm.savedSnapshots.value.isEmpty())
    }

    @Test fun restartingInAnotherRoomRetainsPreviousSessionWithoutRelabelingItsSignals() {
        vm.startScan()
        val firstSession = vm.scanSession.value!!.id
        addBriefHistoricalPacket()
        vm.stopScan()
        awaitCheckpointCount(1)
        val first = disk.load().single()
        vm.updateAreaDraft("B 시험실")
        vm.startScan()
        val secondSession = vm.scanSession.value!!.id
        vm.stopScan()
        awaitCheckpointCount(2)
        val stored = disk.load()
        assertNotEquals(firstSession, secondSession)
        assertEquals(first, stored.single { it.id == "auto-$firstSession" })
        val second = stored.single { it.id == "auto-$secondSession" }
        assertEquals("A 시험실", first.area)
        assertEquals("B 시험실", second.area)
        assertTrue(second.signals.none { it.address == briefAddress })
    }

    @Test fun automaticCheckpointLocksRoomUntilAFreshScanStarts() {
        vm.startScan()
        val firstSession = vm.scanSession.value!!.id
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        awaitCheckpointCount(1)
        assertTrue(vm.scanSession.value!!.areaLocked)

        vm.updateAreaDraft("B 시험실")
        assertEquals("A 시험실", vm.scanSession.value!!.area)
        vm.saveSnapshot("B 시험실", "입실 전", asBaseline = true)
        assertTrue(vm.savedSnapshots.value.isEmpty())
        assertNotNull(vm.snapshotError.value)
        vm.stopScan()
        awaitCheckpointCount(1)
        assertEquals("A 시험실", disk.load().single().area)

        vm.updateAreaDraft("B 시험실")
        vm.startScan()
        assertNotEquals(firstSession, vm.scanSession.value!!.id)
        assertEquals("B 시험실", vm.scanSession.value!!.area)
        assertFalse(vm.scanSession.value!!.areaLocked)
        vm.stopScan()
        awaitCheckpointCount(2)
        assertEquals(setOf("A 시험실", "B 시험실"), disk.load().map { it.area }.toSet())
    }

    @Test fun shutdownFlushesFinalObservationWithoutWaitingForPeriodicCheckpoint() {
        vm.startScan()
        val captured = addBriefHistoricalPacket()
        vm.shutdown()
        awaitCheckpointCount(1)
        assertFalse(vm.scanning.value)
        assertEquals(captured, disk.load().single().signals.single { it.address == briefAddress }.lastSeenMs)
    }

    @Test fun immediateRecreationLoadsCheckpointQueuedByPreviousInstanceShutdown() {
        vm.startScan()
        val captured = addBriefHistoricalPacket()
        vm.shutdown()
        // Deliberately do not wait for the old instance's disk write before recreation.
        vm = AppViewModel(context)
        awaitInitialStorage()
        awaitCheckpointCount(1)
        val restored = vm.checkpoints.value.single().signals.single { it.address == briefAddress }
        assertEquals(captured, restored.lastSeenMs)
        assertEquals(SignalPresence.HISTORY, restored.presence)
        assertFalse(vm.scanning.value)
        assertNull(vm.baselineSnapshot.value)
    }

    @Test fun periodicAndFinalWritesKeepOneCumulativeCheckpointPerSession() {
        vm.startScan()
        val sessionId = vm.scanSession.value!!.id
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        awaitCheckpointCount(1)
        assertTrue(disk.load().single().signals.none { it.address == briefAddress })
        addBriefHistoricalPacket()
        vm.stopScan()
        awaitCheckpointCount(1)
        val latest = disk.load().single()
        assertEquals("auto-$sessionId", latest.id)
        assertTrue(latest.signals.any { it.address == briefAddress })
        assertEquals(6, latest.signals.size)
    }
}
