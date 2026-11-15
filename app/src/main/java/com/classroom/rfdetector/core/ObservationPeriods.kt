package com.classroom.rfdetector.core

data class ObservationPeriod(val firstSeenMs: Long, val lastSeenMs: Long, val count: Int)

fun ScanSource.historyGapMs(): Long = if (this == ScanSource.WIFI) 90_000L else 15_000L

fun signalPresence(source: ScanSource, lastSeenMs: Long, nowMs: Long): SignalPresence {
    val age = (nowMs - lastSeenMs).coerceAtLeast(0)
    return when {
        age <= if (source == ScanSource.WIFI) 45_000 else 5_000 -> SignalPresence.RECENT
        age <= source.historyGapMs() -> SignalPresence.QUIET
        else -> SignalPresence.HISTORY
    }
}

/** Merge late samples too: a delayed packet may bridge two apparent gaps. */
fun addToPeriods(periods: List<ObservationPeriod>, timeMs: Long, gapMs: Long): List<ObservationPeriod> {
    val sorted = (periods + ObservationPeriod(timeMs, timeMs, 1)).sortedBy { it.firstSeenMs }
    val merged = mutableListOf<ObservationPeriod>()
    for (period in sorted) {
        val previous = merged.lastOrNull()
        if (previous != null && period.firstSeenMs - previous.lastSeenMs <= gapMs) {
            merged[merged.lastIndex] = ObservationPeriod(previous.firstSeenMs, maxOf(previous.lastSeenMs, period.lastSeenMs), previous.count + period.count)
        } else merged.add(period)
    }
    return merged
}

/** Union of saved observations between the selected baseline and target, never future/other-room data.
 * Each card keeps the most recent session's statistics; cumulative snapshot counts are not added. */
fun observedUnion(baseline: SignalSnapshot, target: SignalSnapshot, snapshots: List<SignalSnapshot>): List<SnapshotSignal> {
    compareSnapshots(baseline, target) // Same room, mode and chronological direction.
    return (snapshots + baseline + target)
        .filter { it.area.equals(target.area, true) && it.simulation == target.simulation && it.capturedAtMs in baseline.capturedAtMs..target.capturedAtMs }
        .flatMap { snapshot -> snapshot.signals.map { it to snapshot.capturedAtMs } }
        .groupBy { it.first.matchKey }
        .values.map { copies ->
            val latest = copies.maxWith(compareBy<Pair<SnapshotSignal, Long>> { it.first.lastSeenMs }.thenBy { it.second }).first
            latest.copy(presence = signalPresence(latest.source, latest.lastSeenMs, target.capturedAtMs))
        }.sortedBy { it.matchKey }
}
