package com.classroom.rfdetector.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classroom.rfdetector.AppViewModel
import com.classroom.rfdetector.core.DistanceClass
import com.classroom.rfdetector.core.TrackedDevice
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_M = 10.0f
private val RING_M = listOf(1.6f, 3.2f, 8.0f)

/**
 * 레이더 화면. 정면=위쪽, 기기 블립은 (상대방위, 거리) 로 배치.
 * 방위 미확정 기기는 개수와 목록으로 표시. 기준점 신규 기기는 빨간 펄스.
 */
@Composable
fun RadarScreen(vm: AppViewModel, devices: List<TrackedDevice>, headingDeg: Double) {
    val simMode by vm.simMode.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val baselineSet by vm.baselineSet.collectAsState()
    val newCount by vm.newCount.collectAsState()

    val strongest = devices.filter { it.distanceM != null }.minByOrNull { it.distanceM!! }

    val sweep = rememberInfiniteTransition(label = "radar")
    val sweepAngle by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "sweepAngle",
    )
    val pulse by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "pulse",
    )

    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 24f
            color = android.graphics.Color.rgb(200, 212, 226)
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val dimPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 18f
            color = android.graphics.Color.rgb(120, 134, 152)
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        SectionIntro("01 / SIGNAL RADAR", "신호의 변화를 따라가세요", "거리·방향은 추정값입니다. 이동하며 신호를 비교하세요.")
        Spacer(Modifier.height(14.dp))
        NearCard(strongest)

        if (baselineSet && newCount > 0) {
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = RfColors.dangerDim) {
                Text(
                    "기준점 이후 신규 기기 ${newCount}대 · 기기 탭에서 확인",
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = RfColors.danger,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Box(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        ) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "신호 레이더. 감지 ${devices.size}개, 방향 미확정 ${devices.count { it.bearingDeg == null }}개. 상세 정보는 기기 탭에서 확인하세요." }) {
                val r = size.minDimension / 2f
                val c = center
                val scale = (r - 34f) / MAX_M

                drawCircle(color = Color(0xFF0E141D), radius = r, center = c)

                // 회전 스윕
                drawArc(
                    color = RfColors.primary.copy(alpha = 0.07f),
                    startAngle = (if (scanning) sweepAngle else 0f) - 150f,
                    sweepAngle = 60f,
                    useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2f, r * 2f),
                )
                drawLine(
                    color = RfColors.primary.copy(alpha = 0.35f),
                    start = c,
                    end = Offset(
                        c.x + r * sin(Math.toRadians(if (scanning) sweepAngle.toDouble() else 0.0)).toFloat(),
                        c.y - r * cos(Math.toRadians(if (scanning) sweepAngle.toDouble() else 0.0)).toFloat(),
                    ),
                    strokeWidth = 2f,
                )

                // 거리 링 + 라벨(좌하단 대각선)
                RING_M.forEach { m ->
                    drawCircle(
                        color = RfColors.grid,
                        radius = m * scale,
                        center = c,
                        style = Stroke(width = 1.5f),
                    )
                    val lr = m * scale
                    drawContext.canvas.nativeCanvas.drawText(
                        "${if (m == 8.0f) "8" else if (m == 3.2f) "3.2" else "1.6"}m",
                        c.x + lr * 0.7071f,
                        c.y + lr * 0.7071f + 5f,
                        dimPaint,
                    )
                }

                // 북쪽 표시 — 내가 돌면 반대로 도는다
                val nRad = Math.toRadians(-headingDeg)
                drawContext.canvas.nativeCanvas.drawText(
                    "N",
                    c.x + (r - 20f) * sin(nRad).toFloat(),
                    c.y - (r - 20f) * cos(nRad).toFloat() + 7f,
                    dimPaint,
                )

                // 나(중앙) + 정면 방향
                drawCircle(color = RfColors.ok.copy(alpha = 0.25f), radius = 13f, center = c)
                drawCircle(color = RfColors.ok, radius = 7f, center = c)
                val fwd = Path().apply {
                    moveTo(c.x, c.y - 22f)
                    lineTo(c.x - 6f, c.y - 8f)
                    lineTo(c.x + 6f, c.y - 8f)
                    close()
                }
                drawPath(fwd, RfColors.ok.copy(alpha = 0.8f))

                // 기기 블립
                devices.forEach { d ->
                    if (d.rssiEma == null || d.bearingDeg == null) return@forEach
                    val bearing = d.bearingDeg
                    val dist = (d.distanceM?.toFloat() ?: MAX_M).coerceAtMost(MAX_M)
                    val rad = Math.toRadians(bearing)
                    val rr = (dist * scale).coerceAtMost(r - 16f)
                    val pos = Offset(c.x + rr * sin(rad).toFloat(), c.y - rr * cos(rad).toFloat())

                    val accent = when {
                        d.isBaselineNew -> RfColors.danger
                        d.hidden -> RfColors.warning
                        else -> RfColors.primary
                    }
                    val alpha = 0.45f + 0.55f * d.bearingConfidence.toFloat()

                    // 글로우 + NEW 펄스 링 + 코어
                    drawCircle(accent.copy(alpha = alpha * 0.16f), radius = 20f, center = pos)
                    if (d.isBaselineNew) {
                        drawCircle(
                            RfColors.danger.copy(alpha = (1f - pulse) * 0.5f),
                            radius = 12f + 16f * pulse,
                            center = pos,
                        )
                    }
                    drawCircle(
                        accent.copy(alpha = alpha),
                        radius = if (d.distanceClass == DistanceClass.VERY_CLOSE) 11f else 7.5f,
                        center = pos,
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        shortName(d.displayName),
                        pos.x,
                        pos.y + 24f,
                        labelPaint,
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("감지 ${devices.size} · 방향 미확정 ${devices.count { it.bearingDeg == null }}", style = MaterialTheme.typography.labelSmall, color = RfColors.primary)
            Text("범위 약 10m", style = MaterialTheme.typography.labelSmall, color = RfColors.textDim)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !scanning -> "탐색 시작을 눌러 주변 신호를 확인하세요."
                strongest?.bearingDeg == null -> "방향 미확정 신호는 기기 탭에서 확인하세요. 천천히 이동하면 방향을 추정합니다."
                else -> "점은 추정 방향입니다. 근접 진동을 켜면 강한 신호를 진동으로 확인합니다."
            },
            style = MaterialTheme.typography.bodySmall,
            color = RfColors.textDim,
        )

        // 시뮬레이션 조이스틱: 실기기 없이 방위 수렴을 테스트한다
        if (simMode && scanning) {
            Spacer(Modifier.height(8.dp))
            Text("가상 이동 · 한 번에 0.5m / 회전 30°", style = MaterialTheme.typography.labelMedium, color = RfColors.textDim)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            ) {
                CompactBtn("⟲") { vm.simRotate(-30.0) }
                CompactBtn("◀") { vm.simMove(0.0, -0.5) }
                CompactBtn("▲") { vm.simMove(0.5, 0.0) }
                CompactBtn("▼") { vm.simMove(-0.5, 0.0) }
                CompactBtn("▶") { vm.simMove(0.0, 0.5) }
                CompactBtn("⟳") { vm.simRotate(30.0) }
            }
        }
    }
}

/** 최근접 기기 상태 카드 — 한눈에 현재 상황을 보여준다 */
@Composable
private fun NearCard(d: TrackedDevice?) {
    val cls = d?.distanceClass ?: DistanceClass.UNKNOWN
    val accent = classAccent(cls)
    Surface(shape = RoundedCornerShape(20.dp), color = RfColors.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d?.displayName ?: "감지된 신호 없음",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = RfColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subLine(d),
                    style = MaterialTheme.typography.bodySmall,
                    color = RfColors.textDim,
                )
            }
            Text(
                cls.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

private fun subLine(d: TrackedDevice?): String {
    if (d == null) return "BLE · Wi-Fi 스캔 대기 중"
    val trend = d.trendDbPer10s?.let { t ->
        when {
            t > 1.5 -> " · 가까워지는 중"
            t < -1.5 -> " · 멀어지는 중"
            else -> " · 유지"
        }
    } ?: ""
    val bearing = d.bearingDeg?.let { " · 방위 %.0f°".format(it) } ?: ""
    return "%d dBm%s%s".format(d.rssiEma?.roundToInt() ?: 0, trend, bearing)
}

private fun shortName(s: String): String = if (s.length > 12) s.take(11) + "…" else s

@Composable
private fun CompactBtn(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(48.dp).width(48.dp).semantics {
            contentDescription = when (label) {
                "⟲" -> "왼쪽 30도 회전"
                "⟳" -> "오른쪽 30도 회전"
                "◀" -> "왼쪽 0.5미터 이동"
                "▶" -> "오른쪽 0.5미터 이동"
                "▲" -> "앞으로 0.5미터 이동"
                else -> "뒤로 0.5미터 이동"
            }
        },
    ) {
        Text(label, fontSize = 13.sp)
    }
}
