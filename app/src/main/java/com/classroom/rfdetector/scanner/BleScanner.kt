package com.classroom.rfdetector.scanner

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSource

/** BLE 광고 연속 스캔. LOW_LATENCY 로 사실상 연속 수신한다 */
class BleScanner(context: Context) {
    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var callback: ScanCallback? = null

    fun hasHardware(): Boolean = adapter != null

    fun isUsable(): Boolean = try { adapter?.isEnabled == true } catch (_: SecurityException) { false }

    var onError: ((String) -> Unit)? = null

    fun start(onObs: (RfObservation) -> Unit): Boolean {
        stop()
        val ad = adapter ?: return false
        if (!isUsable()) return false
        val scanner = try { ad.bluetoothLeScanner } catch (_: SecurityException) { null } ?: return false
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (callback !== this) return
                val name = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                val now = System.currentTimeMillis()
                val packetAgeNanos = SystemClock.elapsedRealtimeNanos() - result.timestampNanos
                val observedAt = if (result.timestampNanos > 0 && packetAgeNanos >= 0) now - packetAgeNanos / 1_000_000 else now
                onObs(
                    RfObservation(
                        key = result.device.address,
                        source = ScanSource.BLE,
                        name = name,
                        rssi = result.rssi,
                        // AD Tx Power is radiated packet power, not calibrated RSSI at 1m.
                        txPower = null,
                        hidden = false,
                        timestampMs = observedAt,
                        manufacturerIds = result.scanRecord?.manufacturerSpecificData?.let { data ->
                            (0 until data.size()).map { data.keyAt(it) }.toSet()
                        } ?: emptySet(),
                    )
                )
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                if (callback !== this) return
                onError?.invoke("BLE 탐색 오류 ($errorCode) · 잠시 후 다시 시작하세요")
            }
        }
        return try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setReportDelay(0L)
                .build()
            callback = cb
            scanner.startScan(null, settings, cb)
            true
        } catch (e: SecurityException) {
            callback = null
            false
        }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        val ad = adapter ?: return
        try {
            ad.bluetoothLeScanner?.stopScan(cb)
        } catch (_: SecurityException) {
        }
    }
}
