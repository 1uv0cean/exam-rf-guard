package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class SignalAnalysisTest {
    private fun signal(address: String, name: String, source: ScanSource = ScanSource.BLE, rssi: Double? = -60.0, seen: Long = 1000, count: Int = 1) =
        SnapshotSignal(address, source, name, rssi, 500, seen, count, "company", null)
    private val earbud = signal("AA:BB:CC:DD:EE:FF", "Galaxy Buds2 Pro", count = 8)
    private val watch = signal("11:22:33:44:55:66", "Smartwatch", rssi = -40.0, seen = 2000)
    private val ap = signal("22:33:44:55:66:77", "AirPods Pro", ScanSource.WIFI, -30.0)

    @Test fun audioFilterDoesNotTreatManufacturerOrWifiNameAsEarbuds() {
        assertEquals(listOf(earbud), filterAndSortSignals(listOf(earbud, watch, ap), "", RadioFilter.ALL, true, SignalSort.RSSI))
        assertTrue(filterAndSortSignals(listOf(earbud, watch, ap), "", RadioFilter.WIFI, true, SignalSort.RSSI).isEmpty())
    }
    @Test fun searchUsesKoreanBrandAndModelAndUnseparatedMac() {
        assertEquals(listOf(earbud), filterAndSortSignals(listOf(earbud, watch), "삼성 buds2", RadioFilter.BLE, false, SignalSort.RSSI))
        assertTrue(earbud.matchesQuery("aabbccddeeff"))
        assertFalse(earbud.matchesQuery("Sony"))
    }
    @Test fun sortingUsesObservationEvidenceAndNullSignalsLast() {
        val unknown = signal("unknown", "unknown", rssi = null)
        val rows = listOf(earbud, watch, unknown)
        assertEquals(watch, filterAndSortSignals(rows, "", RadioFilter.ALL, false, SignalSort.RSSI).first())
        assertEquals(unknown, filterAndSortSignals(rows, "", RadioFilter.ALL, false, SignalSort.RSSI).last())
        assertEquals(earbud, filterAndSortSignals(rows, "", RadioFilter.ALL, false, SignalSort.OBSERVATIONS).first())
        assertEquals(watch, filterAndSortSignals(rows, "", RadioFilter.ALL, false, SignalSort.LAST_SEEN).first())
        val changes = mapOf(earbud.matchKey to -15.0, watch.matchKey to 7.0)
        assertEquals(earbud, filterAndSortSignals(rows, "", RadioFilter.ALL, false, SignalSort.CHANGE, changes).first())
    }
    @Test fun timelineKeepsMissingEntriesAndExcludesOtherRoomsModesAndFuture() {
        fun snap(id: String, time: Long, signals: List<SnapshotSignal>) = SignalSnapshot(id, "room", id, time, 0, false, signals)
        val before = snap("before", 1000, listOf(watch))
        val after = snap("after", 2000, listOf(watch, earbud))
        val future = snap("future", 3000, listOf(earbud))
        val points = signalTimeline(earbud, listOf(after, before, after, future, after.copy(id = "other-room", area = "other"), after.copy(id = "sim", simulation = true)), after)
        assertEquals(listOf("before", "after"), points.map { it.snapshot.id })
        assertNull(points.first().signal)
        assertEquals(8, points.last().signal!!.observationCount)
    }
}
