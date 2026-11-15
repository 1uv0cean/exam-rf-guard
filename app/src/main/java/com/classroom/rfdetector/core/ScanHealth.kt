package com.classroom.rfdetector.core

data class ScanHealth(
    val label: String,
    val detail: String,
    val warning: Boolean,
    val lastObservedAtMs: Long? = null,
)

/** A running callback is not evidence that the room contains no unobserved devices. */
fun bleScanHealth(
    scanning: Boolean,
    enabled: Boolean,
    active: Boolean,
    lastReceivedAtMs: Long?,
    lastError: String?,
    nowMs: Long,
): ScanHealth = when {
    !scanning -> ScanHealth("탐색 중지", "탐색을 시작해야 새 신호를 기록합니다", true, lastReceivedAtMs)
    !enabled -> ScanHealth("블루투스 확인", "블루투스를 켜고 주변 기기 권한을 확인하세요", true, lastReceivedAtMs)
    lastError != null -> ScanHealth("탐색 오류", lastError, true, lastReceivedAtMs)
    !active -> ScanHealth("수신 중단", "탐색을 중지한 뒤 다시 시작하세요", true, lastReceivedAtMs)
    lastReceivedAtMs == null -> ScanHealth("첫 수신 대기", "연속 탐색 중 · 신호가 없다는 뜻은 아닙니다", true)
    nowMs - lastReceivedAtMs > ScanSource.BLE.historyGapMs() ->
        ScanHealth("최근 수신 없음", "탐색 중 · 마지막 수신 이후 광고가 없을 수 있습니다", true, lastReceivedAtMs)
    else -> ScanHealth("수신 확인", "BLE 광고를 받고 있습니다 · 연결 중인 기기는 광고하지 않을 수 있습니다", false, lastReceivedAtMs)
}

/** A completed empty Wi-Fi scan is distinct from a missing or failed scan. */
fun wifiScanHealth(
    scanning: Boolean,
    enabled: Boolean,
    locationEnabled: Boolean,
    lastUpdatedMs: Long?,
    lastError: String?,
    nowMs: Long,
): ScanHealth = when {
    !scanning -> ScanHealth("탐색 중지", "탐색을 시작해야 결과를 갱신합니다", true, lastUpdatedMs)
    !enabled -> ScanHealth("Wi-Fi 확인", "Wi-Fi를 켜고 다시 확인하세요", true, lastUpdatedMs)
    !locationEnabled -> ScanHealth("위치 설정 확인", "Wi-Fi 탐색에는 위치 서비스와 정확한 위치 권한이 필요합니다", true, lastUpdatedMs)
    lastError != null -> ScanHealth("갱신 대기", lastError, true, lastUpdatedMs)
    lastUpdatedMs == null -> ScanHealth("첫 결과 대기", "완료된 Wi-Fi 결과를 기다리세요 · AP 0개와 다릅니다", true)
    nowMs - lastUpdatedMs > ScanSource.WIFI.historyGapMs() ->
        ScanHealth("결과 갱신 지연", "마지막 갱신이 90초를 넘었습니다 · 시스템 제한과 설정을 확인하세요", true, lastUpdatedMs)
    else -> ScanHealth("갱신 확인", "완료된 AP 탐색 결과입니다 · 모든 휴대폰을 표시하지 않습니다", false, lastUpdatedMs)
}
