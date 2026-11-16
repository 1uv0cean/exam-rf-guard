package com.classroom.rfdetector.core

/** A saved observation, scoped by radio transport rather than product name. */
data class SnapshotSignal(
    val address: String,
    val source: ScanSource,
    val name: String,
    val rssi: Double?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val observationCount: Int,
    val manufacturer: String,
    val modelHint: String?,
    val presence: SignalPresence = SignalPresence.RECENT,
    val manufacturerIds: Set<Int> = emptySet(),
    val periods: List<ObservationPeriod> = emptyList(),
    val referenceRssiAt1m: Double? = null,
    val hints: Set<SignalHint> = emptySet(),
) {
    val matchKey: String get() = radioKey(source, address)
    val reappearanceCount: Int get() = (periods.size - 1).coerceAtLeast(0)
}

data class SignalSnapshot(
    val id: String,
    val area: String,
    val label: String,
    val capturedAtMs: Long,
    val scanStartedAtMs: Long,
    val simulation: Boolean,
    val signals: List<SnapshotSignal>,
) {
    companion object {
        fun capture(id: String, area: String, label: String, capturedAtMs: Long, scanStartedAtMs: Long, simulation: Boolean, devices: List<TrackedDevice>, includeNewHistorySinceMs: Long? = null, includeAllHistory: Boolean = false): SignalSnapshot {
            val signals = devices.filter {
                includeAllHistory || it.presence != SignalPresence.HISTORY ||
                    (includeNewHistorySinceMs != null && it.isBaselineNew && it.lastSeenMs >= includeNewHistorySinceMs)
            }.map { device ->
                    SnapshotSignal(device.key, device.source, device.displayName, device.rssiEma, device.firstSeenMs, device.lastSeenMs,
                        device.observationCount, device.identity.summary, device.identity.modelHint, device.presence, device.manufacturerIds.toSet(),
                        device.periods.toList(), device.referenceRssiAt1m, device.hints.toSet())
            }
            return SignalSnapshot(id, area.trim(), label.trim(), capturedAtMs, scanStartedAtMs, simulation, signals)
        }
    }
}

data class SignalChange(val before: SnapshotSignal, val after: SnapshotSignal) {
    val deltaDb: Double? get() = before.rssi?.let { old -> after.rssi?.minus(old) }
}

data class SnapshotComparison(
    val added: List<SnapshotSignal>,
    val notObserved: List<SnapshotSignal>,
    val common: List<SignalChange>,
)

/** A changed MAC is a changed observation key, never proof of a new physical device. */
fun compareSnapshots(before: SignalSnapshot, after: SignalSnapshot): SnapshotComparison {
    require(before.area.equals(after.area, ignoreCase = true)) { "같은 공간의 스냅샷끼리 비교해 주세요." }
    require(before.simulation == after.simulation) { "실제 탐색과 연습 모드 자료는 비교할 수 없습니다." }
    require(after.capturedAtMs >= before.capturedAtMs) { "비교 대상은 기준 스냅샷 이후 시점이어야 합니다." }
    val previous = before.signals.associateBy { it.matchKey }
    val current = after.signals.associateBy { it.matchKey }
    return SnapshotComparison(
        added = current.filterKeys { it !in previous }.values.sortedByDescending { it.rssi ?: Double.NEGATIVE_INFINITY },
        notObserved = previous.filterKeys { it !in current }.values.toList(),
        common = current.mapNotNull { (key, signal) -> previous[key]?.let { SignalChange(it, signal) } },
    )
}
