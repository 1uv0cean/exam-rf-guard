package com.classroom.rfdetector.core

/** 관측 출처 */
enum class ScanSource { BLE, WIFI, P2P, SIM }

fun radioKey(source: ScanSource, address: String): String = "${source.name}:${address.uppercase(java.util.Locale.ROOT)}"

enum class SignalPresence(val label: String) {
    RECENT("최근 수신"),
    QUIET("수신 대기"),
    HISTORY("수신 기록"),
}

/** pauses·pausedMs: 앱을 벗어나 수신이 멈췄던 횟수와 총 시간. 그 사이의 신호는 기록되지 않는다. */
data class ScanSession(val id: String, val startedAtMs: Long, val area: String, val areaLocked: Boolean = false,
    val pauses: Int = 0, val pausedMs: Long = 0)

/** 거리 등급 — RSSI 기반 추정이며 실제 거리 측정이 아니다. */
enum class DistanceClass(val label: String) {
    VERY_CLOSE("바로 옆"),
    CLOSE("가까움"),
    NEAR("중간"),
    FAR("멀다"),
    UNKNOWN("측정 중"),
}

/** 스캐너/시뮬레이터가 올리는 단일 관측값 */
data class RfObservation(
    val key: String,          // BLE MAC 또는 Wi-Fi BSSID
    val source: ScanSource,
    val name: String?,        // 기기명/SSID. null 이면 미지
    val rssi: Int?,           // null 이면 존재만 확인(P2P 등)
    val txPower: Int?,        // 보정된 1m 기준 RSSI(dBm). 일반 AD 송신 출력값과 다르다.
    val hidden: Boolean,      // Wi-Fi SSID 숨김 AP
    val timestampMs: Long,
    val manufacturerIds: Set<Int> = emptySet(),
    val hints: Set<SignalHint> = emptySet(),
    val details: Map<String, String> = emptyMap(),
) {
    val trackingKey: String get() = radioKey(source, key)
}

/** RSSI 시계열 한 점 */
data class RssiPoint(val tsMs: Long, val rssi: Int)

/** UI 로 내보내는 추적 단위 */
data class TrackedDevice(
    val key: String,
    val displayName: String,
    val source: ScanSource,
    val hidden: Boolean,
    val rssiEma: Double?,
    val trendDbPer10s: Double?,   // + 면 가까워지는 중
    val distanceM: Double?,
    val distanceClass: DistanceClass,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val isBaselineNew: Boolean,   // 기준점 이후 새로 나타난 기기
    val bearingDeg: Double?,      // 상대 방위(정면=0, 시계방향 +). 움직인 양이 부족하면 null
    val bearingConfidence: Double, // 0..1
    val history: List<RssiPoint>,
    val manufacturerIds: Set<Int> = emptySet(),
    val identity: DeviceIdentity = DeviceIdentity(),
    val presence: SignalPresence = SignalPresence.RECENT,
    val lastSeenAgeSeconds: Long = 0,
    val observationCount: Int = 0,
    val peakRssi: Int? = null,
    val periods: List<ObservationPeriod> = emptyList(),
    val referenceRssiAt1m: Double? = null,
    val hints: Set<SignalHint> = emptySet(),
    /** 기기가 알린 나머지 정보(항목 → 값). 화면 표시용이며 저장하지 않는다. */
    val details: Map<String, String> = emptyMap(),
) {
    val trackingKey: String get() = radioKey(source, key)
    /** 가장 강했던 수신도 매우 약함. 멀리(옆 교실·복도) 있을 가능성이 있으나 현장 검증된 기준은 아니다. */
    val weakSignal: Boolean get() = (peakRssi ?: -127) < WEAK_SIGNAL_DBM
}

const val WEAK_SIGNAL_DBM = -85
