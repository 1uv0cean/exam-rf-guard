package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class ExamScenarioTest {
    private fun observation(address: String, time: Long, source: ScanSource = ScanSource.BLE) =
        RfObservation(address, source, if (source == ScanSource.BLE) "AirPods Pro" else "Room AP", -65, null, false, time)

    private fun snapshot(id: String, time: Long, tracker: DeviceTracker, baselineTime: Long? = null) =
        SignalSnapshot.capture(id, "제1시험실", id, time, 0, false, tracker.snapshot(time, true), baselineTime)

    @Test fun examEntryBriefEarbudDisappearsThenReturnsWithoutBecomingAnotherAddress() {
        val tracker = DeviceTracker()
        tracker.update(observation("room", 1000, ScanSource.WIFI))
        val before = snapshot("입실 전", 1000, tracker)
        tracker.setBaseline(before.signals.map { it.matchKey }.toSet())
        tracker.update(observation("earbud", 2000))
        tracker.update(observation("earbud", 2500))
        val disappeared = snapshot("입실 후", 120_000, tracker, 1000)
        val brief = compareSnapshots(before, disappeared).added.single()
        assertEquals(SignalPresence.HISTORY, brief.presence)
        assertEquals(listOf(ObservationPeriod(2000, 2500, 2)), brief.periods)
        assertEquals(ObservationUpdate.REAPPEARED, tracker.update(observation("EARBUD", 121_000)))
        val returned = snapshot("시험 중", 122_000, tracker, 1000)
        val added = compareSnapshots(before, returned).added.single()
        assertEquals(1, added.reappearanceCount)
        assertEquals(3, added.observationCount)
        assertEquals(2, added.periods.size)
        assertEquals(2, observedUnion(before, returned, listOf(disappeared)).size)
        assertEquals(1, brief.periods.size) // Previously saved evidence stays immutable.
    }

    @Test fun latePacketBridgingGapDoesNotLeaveAFalseReappearance() {
        val tracker = DeviceTracker()
        tracker.update(observation("x", 1000))
        tracker.update(observation("x", 30_000))
        assertEquals(2, tracker.snapshot(30_000).single().periods.size)
        tracker.update(observation("x", 15_500))
        val periods = tracker.snapshot(30_000).single().periods
        assertEquals(listOf(ObservationPeriod(1000, 30_000, 3)), periods)
    }

    @Test fun returningSignalDistanceDoesNotBlendStrengthFromBeforeTheGap() {
        val tracker = DeviceTracker()
        tracker.update(observation("x", 1000).copy(rssi = -30))
        tracker.update(observation("x", 30000).copy(rssi = -90))
        assertEquals(-90.0, tracker.snapshot(30000).single().rssiEma!!, 0.0001)
    }

    @Test fun historyThresholdsDoNotTreatNormalWifiCadenceAsReappearance() {
        val tracker = DeviceTracker()
        tracker.update(observation("wifi", 1000, ScanSource.WIFI))
        assertEquals(ObservationUpdate.CURRENT, tracker.update(observation("wifi", 31_000, ScanSource.WIFI)))
        assertEquals(ObservationUpdate.REAPPEARED, tracker.update(observation("wifi", 122_000, ScanSource.WIFI)))
        tracker.update(observation("ble", 1000))
        assertEquals(ObservationUpdate.CURRENT, tracker.update(observation("ble", 16_000)))
        assertEquals(ObservationUpdate.REAPPEARED, tracker.update(observation("ble", 31_001)))
    }

    @Test fun unionIncludesVanishedBaselineAndIntermediateSignalsButExcludesOtherScopes() {
        val tracker = DeviceTracker()
        tracker.update(observation("baseline", 1000))
        val before = snapshot("before", 1000, tracker)
        tracker.update(observation("brief", 2000))
        val middle = snapshot("middle", 2000, tracker)
        val after = snapshot("after", 120_000, tracker)
        assertTrue(after.signals.isEmpty())
        val foreign = middle.copy(id = "other", area = "다른 방", signals = listOf(middle.signals.first().copy(address = "foreign")))
        val practice = foreign.copy(id = "practice", area = before.area, simulation = true)
        val future = foreign.copy(id = "future", area = before.area, capturedAtMs = 130_000)
        val past = foreign.copy(id = "past", area = before.area, capturedAtMs = 500)
        val union = observedUnion(before, after, listOf(middle, foreign, practice, future, past, middle))
        assertEquals(setOf("baseline", "brief"), union.map { it.address }.toSet())
        assertTrue(union.all { it.presence == SignalPresence.HISTORY })
        assertEquals(1, union.first { it.address == "baseline" }.observationCount)
    }

    @Test fun repeatedCumulativeSnapshotsDoNotMultiplyPacketCountsInUnion() {
        val tracker = DeviceTracker()
        tracker.update(observation("x", 1000))
        val before = snapshot("before", 1000, tracker)
        tracker.update(observation("x", 2000))
        val after = snapshot("after", 2000, tracker)
        assertEquals(2, observedUnion(before, after, listOf(before, after)).single().observationCount)
        assertEquals(1, observedUnion(before, after, emptyList()).single().periods.size)
    }
}
