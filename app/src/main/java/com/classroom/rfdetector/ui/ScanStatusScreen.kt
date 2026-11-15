package com.classroom.rfdetector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classroom.rfdetector.AppViewModel
import com.classroom.rfdetector.core.*

/** Radio reception, retained observations and the next comparison action in one place. */
@Composable
fun ScanStatusScreen(vm: AppViewModel, onCompare: () -> Unit, onDevices: () -> Unit) {
    val scanning by vm.scanning.collectAsState()
    val simulation by vm.simMode.collectAsState()
    val devices by vm.devices.collectAsState()
    val session by vm.scanSession.collectAsState()
    val baseline by vm.baselineSnapshot.collectAsState()
    val checkpoints by vm.checkpoints.collectAsState()
    val checkpointBusy by vm.checkpointBusy.collectAsState()
    val checkpointError by vm.checkpointError.collectAsState()
    val tick by vm.healthTick.collectAsState()
    var limitsOpen by rememberSaveable { mutableStateOf(false) }
    val now = maxOf(tick, System.currentTimeMillis())
    val currentCheckpoint = checkpoints.firstOrNull { it.id == "auto-${session?.id}" }
    val modeCheckpoints = checkpoints.filter { it.simulation == simulation }
    val latestCheckpoint = modeCheckpoints.maxByOrNull { it.capturedAtMs }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScanStatusPanel {
                Text("수신 상태", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("입실 전 저장 → 입실 후 비교 → 추가 신호 확인", color = RfColors.primary, style = MaterialTheme.typography.bodySmall)
                if (simulation) Text("연습 데이터 · 실제 무선 수신이 아닙니다", color = RfColors.warning, fontWeight = FontWeight.SemiBold)
                session?.let {
                    Text("${it.area} · ${if (scanning) "탐색 중" else "탐색 중지"}", style = MaterialTheme.typography.bodyMedium)
                    Text("${snapshotTime(it.startedAtMs)}부터의 관측 이력", color = RfColors.textDim, style = MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScanCount("한 번 이상 포착", "${devices.size}개", Modifier.weight(1f))
                    ScanCount("최근 수신", "${devices.count { it.presence == SignalPresence.RECENT }}개", Modifier.weight(1f))
                }
                Text("주소 기준 · 같은 기기가 여러 주소로 보일 수 있습니다", color = RfColors.textDim, style = MaterialTheme.typography.labelSmall)
                if (!scanning && devices.isNotEmpty()) Text("최근·대기·이력 구분은 탐색 중지 당시 기록입니다.", color = RfColors.textDim, style = MaterialTheme.typography.labelSmall)
                if (!scanning) Text("아래 ‘탐색 시작’을 누르면 새 신호를 기록합니다.", color = RfColors.warning, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onCompare, modifier = Modifier.fillMaxWidth()) {
                    RfGlyph(if (baseline == null) RfSymbol.SNAPSHOT else RfSymbol.UNION, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (baseline == null) "① 입실 전 저장으로 이동" else "② 입실 후·시험 중 비교")
                }
                OutlinedButton(onClick = onDevices, modifier = Modifier.fillMaxWidth()) { Text("포착한 신호 전체 보기") }
            }
        }
        item {
            val health = if (simulation) null else bleScanHealth(scanning, vm.ble.isUsable(), vm.ble.active,
                vm.ble.lastReceivedAtMs, vm.ble.lastError, now)
            RadioReceptionCard(RfSymbol.BLUETOOTH, "BLE", health, devices.filter { it.source == ScanSource.BLE }, simulation)
        }
        item {
            val health = if (simulation) null else wifiScanHealth(scanning, vm.wifi.isEnabled(), !vm.locationServicesOff(),
                vm.wifi.lastUpdatedMs, vm.wifi.lastError, now)
            RadioReceptionCard(RfSymbol.WIFI, "Wi-Fi", health, devices.filter { it.source == ScanSource.WIFI }, simulation)
        }
        item {
            ScanStatusPanel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RfGlyph(RfSymbol.CLOCK)
                    Text("자동 보존", fontWeight = FontWeight.SemiBold)
                }
                if (checkpointBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                when {
                    checkpointError != null -> Text(checkpointError!!, color = RfColors.warning, style = MaterialTheme.typography.bodySmall)
                    currentCheckpoint != null -> {
                        Text("${currentCheckpoint.signals.size}개 주소 보존됨", color = RfColors.primary)
                        Text("최근 저장 ${snapshotTime(currentCheckpoint.capturedAtMs)}", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
                    }
                    scanning -> Text("첫 자동 저장 대기 · 5초마다 보존합니다", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
                    else -> Text("탐색을 시작하면 수신 이력을 보존합니다", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
                }
                if (latestCheckpoint != null && currentCheckpoint == null) Text(
                    "이전 보존 · ${latestCheckpoint.area} · ${snapshotTime(latestCheckpoint.capturedAtMs)} · ${latestCheckpoint.signals.size}개 주소",
                    color = RfColors.textDim, style = MaterialTheme.typography.labelSmall)
                Text("잠깐 포착된 신호도 저장 대상입니다. 5초마다·정상 중지 시 보존하며 앱을 벗어나면 수신은 멈춥니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            ScanStatusPanel {
                ExplanationToggle("수신 상태·감지 범위 설명", limitsOpen) { limitsOpen = !limitsOpen }
                if (limitsOpen) {
                    Text("최근 수신 / 수신 대기 / 수신 이력은 마지막으로 잡힌 시각을 기준으로 나눕니다. 이력에 남은 주소가 지금도 그 자리에 있다는 뜻은 아닙니다.", style = MaterialTheme.typography.bodySmall)
                    Text("BLE 광고와 Wi-Fi 접속점을 관측합니다. Wi-Fi 목록은 주변 모든 휴대전화 목록이 아닙니다. 광고를 멈춘 연결 기기·셀룰러·유선 기기·통신 내용은 감지하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                    Text("BLE는 연속 탐색하고 Wi-Fi는 30초 간격으로 요청합니다. Android 제한에 따라 실제 갱신이 늦어질 수 있습니다. 0개 수신은 장비가 없다는 판정이 아닙니다.", style = MaterialTheme.typography.bodySmall)
                    Text("유형·제조사·모델과 거리는 관측 정보에 근거한 후보·추정값입니다. AI 사용이나 소유자를 알아내는 기능은 아닙니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RadioReceptionCard(symbol: RfSymbol, title: String, health: ScanHealth?, devices: List<TrackedDevice>, simulation: Boolean) {
    ScanStatusPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RfGlyph(symbol, color = if (health?.warning == true) RfColors.warning else RfColors.primary)
            Text("$title · ${health?.label ?: "가상 신호"}", fontWeight = FontWeight.SemiBold,
                color = if (health?.warning == true) RfColors.warning else RfColors.primary)
        }
        Text("총 ${devices.size}개 주소", style = MaterialTheme.typography.titleMedium)
        Text("최근 ${devices.count { it.presence == SignalPresence.RECENT }} · 대기 ${devices.count { it.presence == SignalPresence.QUIET }} · 이력 ${devices.count { it.presence == SignalPresence.HISTORY }}",
            color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
        val lastObserved = if (simulation) devices.maxOfOrNull { it.lastSeenMs } else health?.lastObservedAtMs
        Text("${if (simulation) "가상 관측 시각" else if (title == "Wi-Fi") "마지막 결과 갱신" else "마지막 신호 수신"} · ${lastObserved?.let(::snapshotTime) ?: "아직 없음"}",
            color = RfColors.textDim, style = MaterialTheme.typography.labelSmall)
        health?.let { Text(it.detail, color = RfColors.textDim, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ScanCount(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = RfColors.textDim, style = MaterialTheme.typography.labelSmall)
        Text(value, color = RfColors.textPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun ScanStatusPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = RfColors.surface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
