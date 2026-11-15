package com.classroom.rfdetector.core

import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 파티클 필터 방위 추정.
 *
 * 좌표계는 '사용자 기준' 프레임: x = 오른쪽(+), y = 정면(+).
 * - 걸을 때: 사용자가 이동했으니 파티클을 -이동량 만큼 평행이동
 * - 돌 때: 사용자가 시계방향으로 delta 도 돌면 파티클을 +delta 도(수학 반시계) 회전
 * - RSSI 가 올 때: 각 파티클 거리에서 예상 RSSI 를 계산해 관측값과의 오차로 재가중
 *
 * 서 있을 땐 정보가 없어 방위를 내지 않고(totalDisplacement < 1.5m),
 * 걸은 양이 쌓일수록 방위 수렴 + 신뢰도가 올라간다.
 */
class LocalizationEngine(private val particleCount: Int = 250) {

    class DeviceFilter(val n: Int) {
        private val rnd = Random()
        private var px = DoubleArray(n) { rnd.nextGaussian() * 2.0 }
        private var py = DoubleArray(n) { rnd.nextGaussian() * 2.0 }
        private var pw = DoubleArray(n) { 1.0 / n }
        var totalDisplacement = 0.0
            private set

        @Synchronized
        fun observeRssi(rssi: Double, txDbm: Double) {
            val sigma = 4.0   // RSSI 관측 노이즈 표준편차(dB)
            for (i in 0 until n) {
                val d = hypot(px[i], py[i]).coerceAtLeast(0.4)
                val pred = txDbm - 10 * RssiFilter.PATH_N * log10(d)
                val err = rssi - pred
                pw[i] *= exp(-(err * err) / (2 * sigma * sigma))
            }
            normalizeAndMaybeResample()
            jitter()
        }

        /** 사용자가 (right, forward) 미터 이동했을 때 */
        @Synchronized
        fun move(right: Double, forward: Double) {
            val disp = hypot(right, forward)
            if (disp <= 0.0) return
            totalDisplacement += disp
            for (i in 0 until n) {
                px[i] += -right + rnd.nextGaussian() * 0.35
                py[i] += -forward + rnd.nextGaussian() * 0.35
            }
        }

        /** 사용자가 시계방향으로 deltaHeadingDeg 도 회전했을 때 */
        @Synchronized
        fun rotate(deltaHeadingDeg: Double) {
            if (deltaHeadingDeg == 0.0) return
            val a = Math.toRadians(deltaHeadingDeg)
            val c = cos(a); val s = sin(a)
            for (i in 0 until n) {
                val x = px[i]; val y = py[i]
                px[i] = x * c - y * s
                py[i] = x * s + y * c
            }
        }

        private fun jitter() {
            for (i in 0 until n) {
                px[i] += rnd.nextGaussian() * 0.15
                py[i] += rnd.nextGaussian() * 0.15
            }
        }

        private fun normalizeAndMaybeResample() {
            var sum = 0.0
            for (w in pw) sum += w
            if (sum <= 1e-300) { resetUniform(); return }
            var ess = 0.0
            for (i in 0 until n) {
                pw[i] /= sum
                ess += pw[i] * pw[i]
            }
            ess = 1.0 / ess
            if (ess < n / 3.0) systematicResample()
        }

        private fun systematicResample() {
            val idx = IntArray(n)
            var acc = pw[0]
            val step = 1.0 / n
            var u = rnd.nextDouble() * step
            var j = 0
            for (i in 0 until n) {
                while (u > acc && j < n - 1) { j++; acc += pw[j] }
                idx[i] = j
                u += step
            }
            val nx = DoubleArray(n); val ny = DoubleArray(n)
            for (i in 0 until n) { nx[i] = px[idx[i]]; ny[i] = py[idx[i]] }
            px = nx; py = ny
            for (i in 0 until n) pw[i] = 1.0 / n
        }

        private fun resetUniform() {
            for (i in 0 until n) {
                px[i] = rnd.nextGaussian() * 3.0
                py[i] = rnd.nextGaussian() * 3.0
                pw[i] = 1.0 / n
            }
        }

        /** (상대방위 도[정면=0, 시계방향+], 신뢰도 0..1). 움직임 부족 시 null */
        @Synchronized
        fun estimate(): Pair<Double, Double>? {
            if (totalDisplacement < 1.5) return null
            var sx = 0.0; var sy = 0.0
            for (i in 0 until n) { sx += pw[i] * px[i]; sy += pw[i] * py[i] }
            val radialDist = hypot(sx, sy)
            val bearing = Math.toDegrees(atan2(sx, sy))
            // 방위 원통분포의 집중도(mean resultant length)
            var cc = 0.0; var cs = 0.0
            for (i in 0 until n) {
                val b = atan2(px[i], py[i])
                cc += pw[i] * cos(b); cs += pw[i] * sin(b)
            }
            val concentration = sqrt(cc * cc + cs * cs)
            val confidence = concentration *
                minOf(1.0, totalDisplacement / 5.0) *
                minOf(1.0, radialDist / 3.0)
            return bearing to confidence.coerceIn(0.0, 1.0)
        }
    }

    private val filters = HashMap<String, DeviceFilter>()

    @Synchronized
    fun deviceFilter(key: String): DeviceFilter =
        filters.getOrPut(key) { DeviceFilter(particleCount) }

    /** 사용자 이동 — 모든 기기 필터에 동일 적용 */
    @Synchronized
    fun moveAll(right: Double, forward: Double) {
        filters.values.forEach { it.move(right, forward) }
    }

    /** 사용자 회전 — 모든 기기 필터에 동일 적용 */
    @Synchronized
    fun rotateAll(deltaHeadingDeg: Double) {
        filters.values.forEach { it.rotate(deltaHeadingDeg) }
    }

    @Synchronized
    fun remove(key: String) { filters.remove(key) }

    @Synchronized
    fun clear() { filters.clear() }
}
