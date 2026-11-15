package com.classroom.rfdetector

import android.content.ContextWrapper
import android.os.Looper
import com.classroom.rfdetector.core.SignalSnapshot
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
class ScanSessionTest {
    @get:Rule val directory = TemporaryFolder()
    private lateinit var vm: AppViewModel

    @Before fun create() {
        val app = RuntimeEnvironment.getApplication()
        val context = object : ContextWrapper(app) { override fun getFilesDir() = directory.root }
        vm = AppViewModel(context)
        awaitStorage()
        vm.setSim(true)
        vm.startScan()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(5, vm.devices.value.size)
    }
    @After fun close() { vm.shutdown() }

    private fun awaitStorage() {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!vm.snapshotBusy.value) return
            Thread.sleep(5)
        }
        fail("Snapshot operation did not finish")
    }

    private fun saveRoomA(): SignalSnapshot {
        vm.saveSnapshot("A 시험실", "입실 전", asBaseline = true)
        awaitStorage()
        return vm.savedSnapshots.value.single()
    }

    @Test fun cannotRelabelOneRoomsCapturedSignalsAsAnotherRoom() {
        saveRoomA()
        vm.saveSnapshot("B 시험실", "시험 중")
        awaitStorage()
        assertEquals(1, vm.savedSnapshots.value.size)
        assertTrue(vm.snapshotError.value!!.contains("공간"))
    }

    @Test fun selectingOtherRoomBaselineDoesNotApplyItToCurrentScan() {
        val roomA = saveRoomA()
        vm.selectBaseline(roomA.copy(id = "room-b", area = "B 시험실", signals = emptyList()))
        assertFalse(vm.baselineSet.value)
        assertEquals(0, vm.newCount.value)
        assertTrue(vm.devices.value.none { it.isBaselineNew })
        assertEquals(5, vm.devices.value.size)
    }

    @Test fun restartingForAnotherRoomKeepsArchivesAndStartsAnIndependentSession() {
        val original = saveRoomA()
        val originalSession = vm.scanSession.value!!.id
        vm.stopScan()
        vm.updateAreaDraft("B 시험실")
        vm.startScan()
        vm.saveSnapshot("B 시험실", "입실 전", asBaseline = true)
        awaitStorage()
        assertNotEquals(originalSession, vm.scanSession.value!!.id)
        assertEquals("B 시험실", vm.scanSession.value!!.area)
        assertEquals(2, vm.savedSnapshots.value.size)
        assertTrue(vm.savedSnapshots.value.contains(original))
        assertEquals("B 시험실", vm.baselineSnapshot.value!!.area)
    }

    @Test fun previousSessionsPendingSaveDoesNotReplaceNewRoomSelection() {
        vm.saveSnapshot("A 시험실", "입실 전", asBaseline = true)
        vm.stopScan()
        vm.updateAreaDraft("B 시험실")
        vm.startScan()
        val baselineB = SignalSnapshot("b", "B 시험실", "입실 전", System.currentTimeMillis(), System.currentTimeMillis(), true, emptyList())
        vm.selectBaseline(baselineB)
        awaitStorage()
        assertEquals("b", vm.baselineSnapshot.value!!.id)
        assertEquals("B 시험실", vm.scanSession.value!!.area)
        assertEquals("A 시험실", vm.savedSnapshots.value.single().area)
    }
}
