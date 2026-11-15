package com.classroom.rfdetector.core

/** Classify only from evidence the scanner actually obtained. */
fun SnapshotSignal.equipmentType(): String = if (source == ScanSource.WIFI) {
    "Wi-Fi 접속점 · 공유기/핫스폿"
} else audioNameMatch?.kind?.label ?: wearableNameMatch?.kind?.label ?: "Bluetooth 장비 · 유형 미확인"

fun TrackedDevice.equipmentType(): String = if (source == ScanSource.WIFI) {
    "Wi-Fi 접속점 · 공유기/핫스폿"
} else identity.audioNameMatch?.kind?.label ?: identity.wearableNameMatch?.kind?.label ?: "Bluetooth 장비 · 유형 미확인"

fun observationSpan(firstSeenMs: Long, lastSeenMs: Long, count: Int): String {
    if (count <= 1) return "한 번 포착 · 기간 판단 불가"
    val seconds = (lastSeenMs.coerceAtLeast(firstSeenMs) - firstSeenMs) / 1000
    return when {
        seconds < 1 -> "1초 미만 구간에서 $count 회 수신"
        seconds < 60 -> "${seconds}초 구간에서 $count 회 수신"
        seconds < 3600 -> "${seconds / 60}분 ${seconds % 60}초 구간에서 $count 회 수신"
        else -> "${seconds / 3600}시간 ${(seconds % 3600) / 60}분 구간에서 $count 회 수신"
    }
}

fun SignalPresence.observationMeaning(saved: Boolean): String {
    val prefix = if (saved) "저장 당시" else "현재"
    return when (this) {
        SignalPresence.RECENT -> "$prefix 최근 수신 · 최근 관측 범위 안에 신호가 있습니다."
        SignalPresence.QUIET -> "$prefix 수신 대기 · 한동안 새 신호를 받지 못했습니다."
        SignalPresence.HISTORY -> "$prefix 수신 기록만 있음 · 잠깐 포착된 신호도 기록에 남습니다."
    }
}
