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

    @Test(expected = IllegalArgumentException::class) fun unsupportedVersionIsNotSilentlyDiscarded() {
        File(directory.root, "signal-snapshots.json").writeText("{\"version\":99,\"snapshots\":[]}")
        SnapshotStore(directory.root).load()
    }
}
