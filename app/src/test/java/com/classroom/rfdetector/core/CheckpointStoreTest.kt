package com.classroom.rfdetector.core

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckpointStoreTest {
    @get:Rule val directory = TemporaryFolder()
    private fun checkpoint(id: String, at: Long) = SignalSnapshot(id, "시험실", "자동 보존", at, 0, false,
        listOf(SnapshotSignal("AA", ScanSource.BLE, "잠깐 포착", -70.0, 0, at, 1, "미확인", null, SignalPresence.HISTORY)))

    @Test fun recreatedStoreMergesDiskInsteadOfItsEarlierEmptyLoad() {
        val oldActivity = CheckpointStore(directory.root)
        val newActivity = CheckpointStore(File(directory.root, "."))
        assertTrue(newActivity.load().isEmpty())
        val previous = checkpoint("old-session", 1000)
        val current = checkpoint("new-session", 2000)
        oldActivity.upsert(previous)
        assertEquals(listOf(current, previous), newActivity.upsert(current))
        assertEquals(listOf(current, previous), CheckpointStore(directory.root).load())
    }

    @Test fun delayedOlderCheckpointCannotReplaceNewerObservedState() {
        val oldActivity = CheckpointStore(directory.root)
        val newActivity = CheckpointStore(directory.root)
        val latest = checkpoint("session", 2000)
        newActivity.upsert(latest)
        assertEquals(listOf(latest), oldActivity.upsert(checkpoint("session", 1000)))
        assertEquals(latest, CheckpointStore(directory.root).load().single())
    }

    @Test fun concurrentStoreInstancesKeepAllSessionIds() {
        val pool = Executors.newFixedThreadPool(2)
        val gate = CountDownLatch(1)
        try {
            val futures = (0..1).map { writer -> pool.submit {
                val store = CheckpointStore(if (writer == 0) directory.root else File(directory.root, "."))
                gate.await()
                repeat(8) { index -> store.upsert(checkpoint("$writer-$index", (writer * 100 + index).toLong())) }
            } }
            gate.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            val records = CheckpointStore(directory.root).load()
            assertEquals(16, records.size)
            assertEquals(16, records.map { it.id }.distinct().size)
        } finally { pool.shutdownNow() }
    }

    @Test fun futureSchemaIsNotOverwrittenByAnUpsert() {
        val file = File(directory.root, "signal-snapshots.json")
        val original = "{\"version\":99,\"snapshots\":[]}"
        file.writeText(original)
        val store = CheckpointStore(directory.root)
        assertThrows(IllegalArgumentException::class.java) { store.load() }
        assertThrows(IllegalArgumentException::class.java) { store.upsert(checkpoint("new", 1)) }
        assertEquals(original, file.readText())
    }
}
