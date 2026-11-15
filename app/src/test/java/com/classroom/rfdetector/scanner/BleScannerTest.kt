package com.classroom.rfdetector.scanner

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import com.classroom.rfdetector.core.DeviceTracker
import com.classroom.rfdetector.core.RfObservation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BleScannerTest {
    @Test fun advertisementPreservesCompanyAndNameWithoutTreatingRadiatedPowerAsCalibration() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val scanner = BleScanner(context)
        val observations = mutableListOf<RfObservation>()
        assertTrue(scanner.start { observations.add(it) })
        val name = "AirPods Pro".toByteArray()
        val bytes = byteArrayOf(2, 1, 6, 2, 10, 0, 5, -1, 76, 0, 1, 2) +
            byteArrayOf((name.size + 1).toByte(), 9) + name + byteArrayOf(0)
        val record = ReflectionHelpers.callStaticMethod<ScanRecord>(ScanRecord::class.java, "parseFromBytes", ClassParameter.from(ByteArray::class.java, bytes))
        assertEquals(0, record.txPowerLevel)
        val result = ScanResult(adapter.getRemoteDevice("00:11:22:33:44:55"), record, -59, 1000L)
        shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single().onScanResult(1, result)
        val observation = observations.single()
        assertEquals("AirPods Pro", observation.name)
        assertEquals(setOf(76), observation.manufacturerIds)
        assertNull(observation.txPower)
        val tracker = DeviceTracker()
        tracker.update(observation)
        assertEquals(1.0, tracker.snapshot(observation.timestampMs).single().distanceM!!, 0.001)
        scanner.stop()
        assertTrue(shadowOf(adapter.bluetoothLeScanner).scanCallbacks.isEmpty())
    }

    @Test fun batchResultsKeepCaptureTimeAndStoppedCallbacksAreIgnored() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(10))
        val scanner = BleScanner(context)
        val observations = mutableListOf<RfObservation>()
        scanner.start { observations.add(it) }
        val callback = shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single()
        val capturedAtNanos = android.os.SystemClock.elapsedRealtimeNanos() - 2_000_000_000L
        val result = ScanResult(adapter.getRemoteDevice("00:11:22:33:44:55"), null, -80, capturedAtNanos)
        callback.onBatchScanResults(mutableListOf(result))
        assertEquals(1, observations.size)
        assertTrue(System.currentTimeMillis() - observations.single().timestampMs in 2_000..2_500)
        scanner.stop()
        callback.onScanResult(1, result)
        assertEquals(1, observations.size)
        scanner.start { observations.add(it) }
        callback.onBatchScanResults(mutableListOf(result))
        assertEquals(1, observations.size)
        scanner.stop()
    }
}
