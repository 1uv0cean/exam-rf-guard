package com.classroom.rfdetector.core

import java.util.Locale
import kotlin.math.pow

data class DistanceEstimate(val meters: Double, val lowerMeters: Double, val upperMeters: Double, val assumedReference: Boolean) {
    val label: String get() = "약 ${formatMeters(meters)} m"
    val rangeLabel: String get() = "모델 가정 범위 ${formatMeters(lowerMeters)}–${formatMeters(upperMeters)} m"
}

/** Scenario range, not a confidence interval: RSSI ±6 dB, n=2..4, unknown reference ±10 dB. */
fun estimateDistance(rssi: Double?, referenceRssiAt1m: Double? = null): DistanceEstimate? {
    if (rssi == null || !rssi.isFinite() || rssi !in -127.0..-1.0) return null
    val calibrated = referenceRssiAt1m?.takeIf { it.isFinite() && it in -100.0..20.0 }
    val reference = calibrated ?: RssiFilter.DEFAULT_TX_DBM
    val uncertainty = if (calibrated == null) 10.0 else 0.0
    val scenarios = listOf(reference - uncertainty, reference + uncertainty).flatMap { tx ->
        listOf(rssi - 6, rssi + 6).flatMap { received -> listOf(2.0, 4.0).map { n -> 10.0.pow((tx - received) / (10 * n)) } }
    }
    return DistanceEstimate(RssiFilter.distanceM(rssi, reference), scenarios.min(), scenarios.max(), calibrated == null)
}

private fun formatMeters(value: Double): String = when {
    value < 0.1 -> "<0.1"
    value > 100 -> ">100"
    value >= 10 -> String.format(Locale.getDefault(), "%.0f", value)
    else -> String.format(Locale.getDefault(), "%.1f", value)
}
