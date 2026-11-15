package com.classroom.rfdetector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classroom.rfdetector.AppViewModel
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.TrackedDevice
import com.classroom.rfdetector.core.SignalPresence
import com.classroom.rfdetector.core.equipmentType
import com.classroom.rfdetector.core.observationSpan
import com.classroom.rfdetector.core.observationMeaning
import com.classroom.rfdetector.core.estimateDistance
import kotlin.math.roundToInt

@Composable
fun DeviceListScreen(vm: AppViewModel, devices: List<TrackedDevice>, baselineSet: Boolean, newCount: Int) {
    val scanning by vm.scanning.collectAsState()
    val simulation by vm.simMode.collectAsState()
    val snapshotBusy by vm.snapshotBusy.collectAsState()
    val snapshotError by vm.snapshotError.collectAsState()
    var filter by rememberSaveable { mutableStateOf("전체") }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var confirmBaseline by remember { mutableStateOf(false) }
    val visible = devices.filter { device ->
        val matchesFilter = when (filter) {
            "신규" -> device.isBaselineNew
            "BLE" -> device.source == ScanSource.BLE
            "Wi-Fi" -> device.source == ScanSource.WIFI
            "최근 수신" -> device.presence == SignalPresence.RECENT
            "수신 기록" -> device.presence == SignalPresence.HISTORY
            "오디오 후보" -> device.identity.audioNameMatch != null
            "웨어러블 후보" -> device.identity.wearableNameMatch != null
            else -> true
        }
        matchesFilter && device.identity.matchesQuery(query, device.displayName, device.key)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionIntro("02 / DEVICE INVENTORY", "주변 기기", "최근 수신 ${devices.count { it.presence == SignalPresence.RECENT }}개 · 세션 기록 ${devices.size}개. 잠깐 포착된 신호도 남깁니다.") }
        item {
            Surface(color = RfColors.surface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (baselineSet) "기준점 저장됨 · 신규 ${newCount}대" else "탐색 목록을 기준점으로 저장하세요", fontWeight = FontWeight.SemiBold, color = if (newCount > 0) RfColors.warning else RfColors.textPrimary)
                    if (!baselineSet) Text("빈 시험장 기준은 ‘비교’ 탭에서 공간·시점과 함께 저장할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = RfColors.textDim)
                    OutlinedButton(onClick = { if (baselineSet) confirmBaseline = true else vm.takeBaseline() }, enabled = scanning && devices.isNotEmpty() && !snapshotBusy) { Text(if (baselineSet) "기준점 다시 저장" else "기준점 저장") }
                    if (simulation && scanning) TextButton(onClick = { vm.simAddDevice() }) { Text("+ 가상 이어폰 추가 · 신규 감지 체험") }
                    snapshotError?.let { Text(it, color = RfColors.warning, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("이름 · 제조사 · 모델 · MAC 검색") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp)) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("전체", "신규", "오디오 후보", "웨어러블 후보", "최근 수신", "수신 기록", "BLE", "Wi-Fi").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(if (label == "신규") "신규 $newCount" else label) }) }
            }
        }
        item { Text("${visible.size}개 표시 · 기기를 눌러 신호 이력 확인", style = MaterialTheme.typography.labelSmall, color = RfColors.textDim) }
        if (visible.isEmpty()) item { EmptyState(if (devices.isEmpty()) "아직 감지된 기기가 없습니다" else "조건에 맞는 기기가 없습니다", if (devices.isEmpty()) "탐색을 시작한 뒤 잠시 기다려 주세요. 연습 모드로 기능을 먼저 체험할 수 있습니다." else "검색어나 필터를 바꿔 보세요.") }
        items(visible, key = { it.trackingKey }) { device -> DeviceRow(device) { selectedKey = device.trackingKey } }
    }
    val selected = devices.firstOrNull { it.trackingKey == selectedKey }
    if (selected != null) AlertDialog(onDismissRequest = { selectedKey = null }, title = { Text(selected.displayName) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("유형: ${selected.equipmentType()}", color = RfColors.primary)
            Text(observationSpan(selected.firstSeenMs, selected.lastSeenMs, selected.observationCount), color = RfColors.primary)
            Text("${selected.periods.size}개 관측 구간 · 재등장 ${(selected.periods.size - 1).coerceAtLeast(0)}회", color = RfColors.warning)
            estimateDistance(selected.rssiEma, selected.referenceRssiAt1m)?.let {
                Text("예상 거리 ${it.label} · 마지막 수신 기준\n${it.rangeLabel}", color = RfColors.primary)
                Text("RSSI와 실내 전파 모델의 추정입니다. ${if (it.assumedReference) "기기별 보정 없이 1m −59 dBm을 가정합니다." else "1m RSSI 기준을 적용합니다."} 벽·인체·송신 출력에 따라 실제 거리와 크게 다를 수 있습니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
            }
            Text("최초 ${snapshotTime(selected.firstSeenMs)}\n마지막 ${snapshotTime(selected.lastSeenMs)}", style = MaterialTheme.typography.bodySmall)
            Text("관측 구간이며 계속 연결된 시간은 아닙니다. 수신 사이의 공백은 알 수 없습니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
            Text(selected.key, style = MaterialTheme.typography.bodySmall, color = RfColors.textDim)
            Text("제조사 식별", fontWeight = FontWeight.SemiBold)
            Text("BLE 광고 회사: ${selected.identity.advertisedCompanies.joinToString().ifBlank { "정보 없음" }}")
            Text("MAC 등록사: ${selected.identity.macOrganization ?: "확인 불가"}")
            val wearable = selected.identity.wearableNameMatch
            if (wearable != null) WearableIdentityPanel(wearable)
            else AudioIdentityPanel(selected.identity.audioNameMatch, selected.identity.modelHint, selected.identity.evidenceNote)
            Text("모델 후보는 BLE 광고 이름에서 읽은 값입니다. 광고 회사 ID는 광고 데이터의 회사 식별자이며 실제 제품 제조사와 다를 수 있습니다. ${selected.identity.addressNote}", style = MaterialTheme.typography.bodySmall, color = RfColors.textDim)
            Text("${selected.source} · ${selected.distanceClass.label} · ${selected.rssiEma?.roundToInt() ?: "—"} dBm")
            Text("${selected.presence.label} · 마지막 수신 ${selected.lastSeenAgeSeconds}초 전\n총 ${selected.observationCount}회 · 최고 ${selected.peakRssi ?: "—"} dBm", color = RfColors.primary)
            Text(selected.presence.observationMeaning(false), style = MaterialTheme.typography.bodySmall, color = RfColors.textDim)
            SignalHistory(selected)
            Text("최근 10초 신호 이력 · ${selected.history.size}개 표본", style = MaterialTheme.typography.labelSmall)
            Text(selected.bearingDeg?.let { "추정 방위 ${it.roundToInt()}° · 신뢰도 ${(selected.bearingConfidence * 100).roundToInt()}%" } ?: "방향 미확정 · 천천히 이동하며 관측하세요.")
            Text("거리·방향은 신호 기반 추정값입니다. 신호가 강할수록 그래프가 위로 올라갑니다.", style = MaterialTheme.typography.bodySmall, color = RfColors.textDim)
        }
    }, confirmButton = { TextButton(onClick = { selectedKey = null }) { Text("닫기") } })
    if (confirmBaseline) AlertDialog(onDismissRequest = { confirmBaseline = false }, title = { Text("기준점을 다시 저장할까요?") }, text = { Text("현재 감지된 기기 ${devices.size}대를 새 기준으로 저장하고 신규 표시를 초기화합니다.") }, confirmButton = { TextButton(onClick = { vm.takeBaseline(); confirmBaseline = false }) { Text("다시 저장") } }, dismissButton = { TextButton(onClick = { confirmBaseline = false }) { Text("취소") } })
}

@Composable
private fun DeviceRow(device: TrackedDevice, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = RfColors.surface, modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "기기 상세 보기", onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(device.source.name + (if (device.isBaselineNew) "  ·  신규" else "") + (device.identity.audioNameMatch?.let { " · ${it.kind.label}" } ?: ""), style = MaterialTheme.typography.labelSmall, color = if (device.isBaselineNew) RfColors.warning else RfColors.primary)
                Text(device.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("유형: ${device.equipmentType()}", style = MaterialTheme.typography.labelSmall, color = RfColors.primary)
                Text(observationSpan(device.firstSeenMs, device.lastSeenMs, device.observationCount), style = MaterialTheme.typography.labelSmall, color = RfColors.textDim)
                if (device.periods.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RfGlyph(RfSymbol.RETURN, size = 16.dp, color = RfColors.warning)
                    Text("재등장 ${device.periods.size - 1}회", style = MaterialTheme.typography.labelSmall, color = RfColors.warning)
                }
                estimateDistance(device.rssiEma, device.referenceRssiAt1m)?.let { Text("${it.label} · 마지막 수신 추정", style = MaterialTheme.typography.labelSmall, color = RfColors.textDim) }
                Text(device.identity.summary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = RfColors.textDim)
                Text("${device.presence.label} · ${device.observationCount}회 · ${device.lastSeenAgeSeconds}초 전", style = MaterialTheme.typography.labelSmall, color = if (device.presence == SignalPresence.RECENT) RfColors.primary else RfColors.textDim)
                Text("${device.distanceClass.label} · ${if (device.hidden) "숨김 AP · " else ""}${trendLabel(device.trendDbPer10s)}", style = MaterialTheme.typography.bodySmall, color = RfColors.textDim)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(device.rssiEma?.roundToInt()?.toString() ?: "—", style = MaterialTheme.typography.titleLarge, color = classAccent(device.distanceClass), fontWeight = FontWeight.Bold)
                Text("dBm  ›", style = MaterialTheme.typography.labelSmall, color = RfColors.textDim)
            }
        }
    }
}

private fun trendLabel(trend: Double?) = when {
    trend == null -> "관측 중"
    trend > 1.5 -> "신호 강해짐 ↗"
    trend < -1.5 -> "신호 약해짐 ↘"
    else -> "신호 유지 →"
}

@Composable
private fun SignalHistory(device: TrackedDevice) {
    Canvas(Modifier.fillMaxWidth().height(90.dp).semantics { contentDescription = "최근 신호 세기 ${device.history.size}개 표본" }) {
        (0..2).forEach { row -> val y = size.height * row / 2; drawLine(RfColors.grid, Offset(0f, y), Offset(size.width, y)) }
        val values = device.history
        if (values.isNotEmpty()) {
            val path = Path()
            val firstTime = values.first().tsMs
            val duration = (values.last().tsMs - firstTime).coerceAtLeast(1)
            values.forEachIndexed { index, point ->
                val x = if (values.size == 1) size.width / 2 else size.width * (point.tsMs - firstTime).toFloat() / duration
                val y = size.height * (1f - ((point.rssi + 100) / 70f).coerceIn(0f, 1f))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                if (values.size == 1) drawCircle(RfColors.primary, radius = 3.dp.toPx(), center = Offset(x, y))
            }
            drawPath(path, RfColors.primary, style = Stroke(2.dp.toPx()))
        }
    }
}
