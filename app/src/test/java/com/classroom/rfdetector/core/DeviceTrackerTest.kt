package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class DeviceTrackerTest {
    private fun observation(key: String, rssi: Int?, time: Long = 1000) = RfObservation(key, ScanSource.BLE, key, rssi, null, false, time)

    @Test fun strongestSignalFirstWithUnknownLast() {
        val tracker = DeviceTracker()
        tracker.update(observation("weak", -90))
        tracker.update(observation("strong", -40))
        tracker.update(observation("unknown", null))
        assertEquals(listOf("strong", "weak", "unknown"), tracker.snapshot(1000).map { it.key })
    }

    @Test fun newDevicesTakePriorityOverStrongBaselineDevices() {
        val tracker = DeviceTracker()
        tracker.update(observation("existing", -40))
        tracker.takeBaseline()
        tracker.update(observation("new", -90))
        val devices = tracker.snapshot(1000)
        assertEquals("new", devices.first().key)
        assertTrue(devices.first().isBaselineNew)
        assertFalse(devices.last().isBaselineNew)
        tracker.takeBaseline()
        assertTrue(tracker.snapshot(1000).none { it.isBaselineNew })
    }

    @Test fun staleDevicesExpireButReturningBaselineDeviceIsNotNew() {
        val tracker = DeviceTracker()
        tracker.update(observation("existing", -40))
        tracker.takeBaseline()
        assertTrue(tracker.snapshot(91_001).isEmpty())
        tracker.update(observation("existing", -45, 92_000))
        assertFalse(tracker.snapshot(92_000).single().isBaselineNew)
    }

    @Test fun newSessionClearsBaselineAndHistory() {
        val tracker = DeviceTracker()
        tracker.update(observation("old", -60))
        tracker.takeBaseline()
        tracker.clear()
        assertNull(tracker.baselineKeys)
        assertTrue(tracker.snapshot(1000).isEmpty())
        tracker.update(observation("next", -50))
        assertFalse(tracker.snapshot(1000).single().isBaselineNew)
    }

    @Test fun singleBriefSignalSurvivesExpiryInSessionHistory() {
        val tracker = DeviceTracker()
        tracker.update(observation("baseline", -65))
        tracker.takeBaseline()
        assertEquals(ObservationUpdate.FIRST_SEEN, tracker.update(observation("brief-earbud", -80, 1_010)))
        val record = tracker.snapshot(120_000, includeInactive = true).first { it.key == "brief-earbud" }
        assertEquals(SignalPresence.HISTORY, record.presence)
        assertTrue(record.isBaselineNew)
        assertEquals(1, record.observationCount)
        assertEquals(-80, record.peakRssi)
        assertEquals(1_010L, record.lastSeenMs)
        assertTrue(tracker.snapshot(120_000).isEmpty())
    }

    @Test fun repeatPacketsUpdateStrengthWithoutCreatingAnotherDevice() {
        val tracker = DeviceTracker()
        assertEquals(ObservationUpdate.FIRST_SEEN, tracker.update(observation("earbud", -85, 1000)))
        assertEquals(ObservationUpdate.CURRENT, tracker.update(observation("earbud", -55, 1100)))
        val record = tracker.snapshot(1200).single()
        assertEquals(2, record.observationCount)
        assertEquals(-55, record.peakRssi)
        assertEquals(SignalPresence.RECENT, record.presence)
        assertEquals(SignalPresence.QUIET, tracker.snapshot(10_000).single().presence)
        assertEquals(SignalPresence.HISTORY, tracker.snapshot(20_000).single().presence)
    }

    @Test fun wifiUsesSlowerFreshnessWindowThanBle() {
        val tracker = DeviceTracker()
        tracker.update(observation("ble", -60))
        tracker.update(observation("wifi", -65).copy(source = ScanSource.WIFI))
        val records = tracker.snapshot(31_000).associateBy { it.key }
        assertEquals(SignalPresence.HISTORY, records.getValue("ble").presence)
        assertEquals(SignalPresence.RECENT, records.getValue("wifi").presence)
    }

    @Test fun identicalAddressAcrossRadiosDoesNotMergeEvidence() {
        val tracker = DeviceTracker()
        tracker.update(observation("AA:BB:CC:DD:EE:FF", -50).copy(name = "AirPods Pro", manufacturerIds = setOf(76)))
        tracker.takeBaseline()
        tracker.update(observation("AA:BB:CC:DD:EE:FF", -90, 2000).copy(source = ScanSource.WIFI, name = "Room AP", hidden = true))
        val records = tracker.snapshot(2000)
        assertEquals(2, records.size)
        val ble = records.single { it.source == ScanSource.BLE }
        val wifi = records.single { it.source == ScanSource.WIFI }
        assertEquals("AirPods Pro", ble.displayName)
        assertEquals(-50.0, ble.rssiEma!!, 0.001)
        assertEquals(setOf(76), ble.manufacturerIds)
        assertFalse(ble.hidden)
        assertFalse(ble.isBaselineNew)
        assertEquals("Room AP", wifi.displayName)
        assertEquals(-90.0, wifi.rssiEma!!, 0.001)
        assertTrue(wifi.manufacturerIds.isEmpty())
        assertTrue(wifi.hidden)
        assertTrue(wifi.isBaselineNew)
    }

    @Test fun latePacketsKeepHistoryButDoNotOverwriteLatestState() {
        val tracker = DeviceTracker()
        tracker.update(observation("device", -60, 3000).copy(name = "current"))
        assertEquals(ObservationUpdate.LATE, tracker.update(observation("device", -30, 2000).copy(name = "old")))
        val record = tracker.snapshot(3100).single()
        assertEquals("current", record.displayName)
        assertEquals(-60.0, record.rssiEma!!, 0.001)
        assertEquals(2000L, record.firstSeenMs)
        assertEquals(3000L, record.lastSeenMs)
        assertEquals(listOf(2000L, 3000L), record.history.map { it.tsMs })
        assertEquals(2, record.observationCount)
        assertEquals(-30, record.peakRssi)
    }

    @Test fun addressCasingDoesNotCreateAnotherRecord() {
        val tracker = DeviceTracker()
        tracker.update(observation("AA:BB:CC:DD:EE:FF", -60))
        tracker.update(observation("aa:bb:cc:dd:ee:ff", -65, 2000))
        assertEquals(1, tracker.snapshot(2000).size)
        assertEquals(2, tracker.snapshot(2000).single().observationCount)
    }
}
