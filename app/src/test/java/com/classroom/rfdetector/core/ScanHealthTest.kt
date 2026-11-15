package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class ScanHealthTest {
    @Test fun missingFirstBlePacketIsWaitingRatherThanClearRoom() {
        val health = bleScanHealth(true, true, true, null, null, 100_000)
        assertEquals("첫 수신 대기", health.label)
        assertTrue(health.warning)
        assertNull(health.lastObservedAtMs)
    }

    @Test fun failedAndStoppedBleCannotUseRecentReceptionToClaimRunning() {
        assertEquals("탐색 오류", bleScanHealth(true, true, false, 100_000, "error", 100_001).label)
        assertEquals("수신 중단", bleScanHealth(true, true, false, 100_000, null, 100_001).label)
        assertEquals("탐색 중지", bleScanHealth(false, true, false, 100_000, null, 100_001).label)
    }

    @Test fun bleReceptionAgeDoesNotImplyDeviceLeftRoom() {
        assertFalse(bleScanHealth(true, true, true, 100_000, null, 115_000).warning)
        val old = bleScanHealth(true, true, true, 100_000, null, 115_001)
        assertEquals("최근 수신 없음", old.label)
        assertEquals(100_000L, old.lastObservedAtMs)
    }

    @Test fun wifiDistinguishesWaitingSuccessfulCompletionAndStaleResults() {
        assertEquals("첫 결과 대기", wifiScanHealth(true, true, true, null, null, 100_000).label)
        assertFalse(wifiScanHealth(true, true, true, 100_000, null, 190_000).warning)
        assertEquals("결과 갱신 지연", wifiScanHealth(true, true, true, 100_000, null, 190_001).label)
        assertEquals("갱신 대기", wifiScanHealth(true, true, true, 100_000, "system limit", 100_001).label)
    }

    @Test fun disabledWifiAndLocationTakePrecedenceOverCachedSuccess() {
        assertEquals("Wi-Fi 확인", wifiScanHealth(true, false, true, 100_000, null, 100_001).label)
        assertEquals("위치 설정 확인", wifiScanHealth(true, true, false, 100_000, null, 100_001).label)
    }
}
