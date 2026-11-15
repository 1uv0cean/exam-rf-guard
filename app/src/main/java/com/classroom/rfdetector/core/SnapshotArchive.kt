package com.classroom.rfdetector.core

/** Archive filtering never changes which snapshots are selected for comparison. */
fun filterSnapshotArchive(snapshots: List<SignalSnapshot>, simulation: Boolean, query: String, area: String? = null): List<SignalSnapshot> = snapshots
    .filter { snapshot ->
        snapshot.simulation == simulation && (area == null || snapshot.area.equals(area.trim(), true)) &&
            fieldsMatchQuery(query, listOf(snapshot.area, snapshot.label))
    }
    .sortedWith(compareByDescending<SignalSnapshot> { it.capturedAtMs }.thenBy { it.id })
