package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class SignalSnapshotTest {
    private fun signal(address: String, source: ScanSource = ScanSource.BLE, rssi: Double? = -65.0) =
        SnapshotSignal(address, source, "earbud", rssi, 1000, 2000, 3, "advertised company", "model candidate")
    private fun snapshot(id: String, time: Long, vararg entries: SnapshotSignal) =
        SignalSnapshot(id, "제1시험실", id, time, 500, false, entries.toList())

    @Test fun comparesAddedMissingAndCommonAddressesWithStrengthDelta() {
        val before = snapshot("입실 전", 2000, signal("AA"), signal("BB"))
        val after = snapshot("시험 중", 3000, signal("aa", rssi = -55.0), signal("CC"))
        val comparison = compareSnapshots(before, after)
        assertEquals(listOf("CC"), comparison.added.map { it.address })
        assertEquals(listOf("BB"), comparison.notObserved.map { it.address })
        assertEquals(10.0, comparison.common.single().deltaDb!!, 0.001)
    }

    @Test fun matchingUsesAddressAndTransportInsteadOfName() {
        val before = snapshot("before", 2000, signal("AA", ScanSource.WIFI))
        val after = snapshot("after", 3000, signal("AA", ScanSource.BLE))
        assertEquals(1, compareSnapshots(before, after).added.size)
        assertEquals(1, compareSnapshots(before, after).notObserved.size)
    }

    @Test fun missingRssiDoesNotInventStrengthChange() {
        val before = snapshot("before", 2000, signal("AA", rssi = null))
        val after = snapshot("after", 3000, signal("AA"))
        assertNull(compareSnapshots(before, after).common.single().deltaDb)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsDifferentRooms() {
        compareSnapshots(snapshot("before", 2000), snapshot("after", 3000).copy(area = "제2시험실"))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsMixedSimulationAndRealData() {
        compareSnapshots(snapshot("before", 2000), snapshot("after", 3000).copy(simulation = true))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsReversedTimeOrder() {
        compareSnapshots(snapshot("before", 3000), snapshot("after", 2000))
    }

    @Test fun captureIsImmutableAndExcludesOldHistory() {
        val tracker = DeviceTracker()
        tracker.update(RfObservation("brief", ScanSource.BLE, "old", -70, null, false, 1000))
        tracker.update(RfObservation("present", ScanSource.BLE, "current", -55, null, false, 20_000))
        val frozen = SignalSnapshot.capture("id", " 제1시험실 ", " 입실 전 ", 20_000, 500, false, tracker.snapshot(20_000, true))
        tracker.update(RfObservation("present", ScanSource.BLE, "changed", -40, null, false, 21_000))
        assertEquals("제1시험실", frozen.area)
        assertEquals("입실 전", frozen.label)
        assertEquals("current", frozen.signals.single().name)
        assertEquals(-55.0, frozen.signals.single().rssi!!, 0.001)
        assertEquals("present", frozen.signals.single().address)
    }

    @Test fun savedBaselineStillRecognizesAddressAfterNewScan() {
        val tracker = DeviceTracker()
        tracker.setBaseline(setOf(signal("AA").matchKey))
        tracker.update(RfObservation("aa", ScanSource.BLE, "renamed", -60, null, false, 1000))
        assertFalse(tracker.snapshot(1000).single().isBaselineNew)
        assertEquals(ObservationUpdate.FIRST_SEEN, tracker.update(RfObservation("aa", ScanSource.WIFI, "hotspot", -65, null, false, 1100)))
        assertTrue(tracker.snapshot(1100).single { it.source == ScanSource.WIFI }.isBaselineNew)
        assertFalse(tracker.snapshot(1100).single { it.source == ScanSource.BLE }.isBaselineNew)
    }

    @Test fun briefNewSignalIsStillIncludedInLaterComparisonButNotInNewBaseline() {
        val tracker = DeviceTracker()
        tracker.update(RfObservation("room", ScanSource.WIFI, "router", -65, null, false, 1000))
        tracker.takeBaseline()
        val before = SignalSnapshot.capture("before", "제1시험실", "입실 전", 1000, 500, false, tracker.snapshot(1000, true))
        tracker.update(RfObservation("brief", ScanSource.BLE, "AirPods Pro", -60, null, false, 1100, setOf(76)))
        val records = tracker.snapshot(60_000, true)
        val after = SignalSnapshot.capture("after", "제1시험실", "시험 중", 60_000, 500, false, records, includeNewHistorySinceMs = 1000)
        val added = compareSnapshots(before, after).added.single()
        assertEquals("brief", added.address)
        assertEquals(SignalPresence.HISTORY, added.presence)
        assertEquals(setOf(76), added.manufacturerIds)
        val newBaseline = SignalSnapshot.capture("new", "제1시험실", "새 기준", 60_000, 500, false, records)
        assertFalse(newBaseline.signals.any { it.address == "brief" })
    }

    @Test fun sameAddressOnTwoRadiosProducesTwoIndependentSnapshotEntries() {
        val tracker = DeviceTracker()
        tracker.update(RfObservation("AA", ScanSource.BLE, "AirPods Pro", -55, null, false, 1000, setOf(76)))
        val before = SignalSnapshot.capture("before", "제1시험실", "입실 전", 1000, 500, false, tracker.snapshot(1000))
        tracker.update(RfObservation("AA", ScanSource.WIFI, "Room AP", -85, null, true, 2000))
        val after = SignalSnapshot.capture("after", "제1시험실", "시험 중", 2000, 500, false, tracker.snapshot(2000))
        assertEquals(2, after.signals.size)
        val comparison = compareSnapshots(before, after)
        assertEquals("Room AP", comparison.added.single().name)
        assertTrue(comparison.added.single().manufacturerIds.isEmpty())
        assertEquals("AirPods Pro", comparison.common.single().after.name)
        assertEquals(setOf(76), comparison.common.single().after.manufacturerIds)
        assertEquals(0.0, comparison.common.single().deltaDb!!, 0.001)
    }
}
