package com.classroom.rfdetector.core

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnapshotStoreTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun savedSnapshotsSurviveNewStoreInstanceWithNullFieldsAndUnicode() {
        val snapshot = SignalSnapshot("id", "3층 제1시험실", "입실 전", 3000, 1000, true,
            listOf(SnapshotSignal("AA:BB:CC:DD:EE:FF", ScanSource.BLE, "이어폰", null, 1000, 2000, 1, "Apple · 광고", null)))
        SnapshotStore(directory.root).save(listOf(snapshot))
        assertEquals(listOf(snapshot), SnapshotStore(directory.root).load())
        val next = snapshot.copy(id = "after", label = "시험 중", capturedAtMs = 4000)
        SnapshotStore(directory.root).save(listOf(next, snapshot))
        assertEquals(listOf(next, snapshot), SnapshotStore(directory.root).load())
    }

    @Test fun missingFileIsAnEmptyArchive() {
        assertTrue(SnapshotStore(directory.root).load().isEmpty())
    }

    @Test fun observationPeriodsAndDistanceReferenceSurviveReopen() {
        val signal = SnapshotSignal("earbud", ScanSource.BLE, "AirPods Pro", -65.0, 1000, 30000, 3, "Apple", null,
            periods = listOf(ObservationPeriod(1000, 1100, 2), ObservationPeriod(30000, 30000, 1)), referenceRssiAt1m = -60.0)
        val snapshot = SignalSnapshot("id", "시험실", "시험 중", 40000, 0, false, listOf(signal))
        SnapshotStore(directory.root).save(listOf(snapshot))
        assertEquals(snapshot, SnapshotStore(directory.root).load().single())
        assertEquals(1, SnapshotStore(directory.root).load().single().signals.single().reappearanceCount)
    }

    @Test fun oldVersionRemainsReadableWithoutInventingObservationPeriods() {
        File(directory.root, "signal-snapshots.json").writeText("""{"version":1,"snapshots":[{"id":"old","area":"시험실","label":"입실 전","capturedAtMs":2000,"scanStartedAtMs":0,"simulation":false,"signals":[{"address":"old","source":"BLE","name":"old","rssi":-65,"firstSeenMs":1000,"lastSeenMs":2000,"observations":3,"manufacturer":"unknown","model":null}]}]}""")
        val signal = SnapshotStore(directory.root).load().single().signals.single()
        assertTrue(signal.periods.isEmpty())
        assertNull(signal.referenceRssiAt1m)
    }

    @Test(expected = IllegalArgumentException::class) fun unsupportedVersionIsNotSilentlyDiscarded() {
        File(directory.root, "signal-snapshots.json").writeText("{\"version\":99,\"snapshots\":[]}")
        SnapshotStore(directory.root).load()
    }
}
