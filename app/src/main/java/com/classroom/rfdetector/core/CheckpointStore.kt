package com.classroom.rfdetector.core

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** All instances merge against disk under a process-wide file lock, including recreated activities. */
class CheckpointStore(directory: File) {
    private val snapshots = SnapshotStore(directory)
    private val lock = locks.getOrPut(File(directory, "signal-snapshots.json").canonicalPath) { Any() }

    fun load(): List<SignalSnapshot> = synchronized(lock) { snapshots.load() }

    fun upsert(record: SignalSnapshot): List<SignalSnapshot> = synchronized(lock) {
        // A failed/unsupported load aborts before writing, preserving the existing file.
        val latest = snapshots.load()
        val existing = latest.firstOrNull { it.id == record.id }
        if (existing != null && existing.capturedAtMs > record.capturedAtMs) return@synchronized latest
        val merged = (latest.filterNot { it.id == record.id } + record)
            .sortedWith(compareByDescending<SignalSnapshot> { it.capturedAtMs }.thenBy { it.id })
        snapshots.save(merged)
        merged
    }

    companion object {
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
