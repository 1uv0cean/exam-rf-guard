package com.classroom.rfdetector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classroom.rfdetector.AppViewModel

@Composable
fun ChannelScreen(vm: AppViewModel) {
    val rows by vm.wifiStats.collectAsState()
    val simulation by vm.simMode.collectAsState()
    val counts = rows.filter { it.freq in 2412..2484 }.groupingBy { channelNumber(it.freq) }.eachCount()
    val maxCount = (counts.values.maxOrNull() ?: 1).coerceAtLeast(1)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionIntro("03 / WI-FI SPECTRUM", "주변 무선 환경", if (simulation) "연습 모드 · 가상 AP 2개의 채널을 표시합니다." else "Wi-Fi AP의 채널별 수를 표시합니다. 약 30초마다 갱신을 요청합니다.") }
        item {
            Surface(color = RfColors.surface, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("2.4 GHz", fontWeight = FontWeight.Bold)
                        Text("채널별 AP 수", style = MaterialTheme.typography.labelSmall, color = RfColors.textDim)
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 154.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                        (1..14).forEach { channel ->
                            val count = counts[channel] ?: 0
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(if (count == 0) "" else "$count", style = MaterialTheme.typography.labelSmall, color = RfColors.primary)
                                Surface(color = if (count == 0) RfColors.surfaceAlt else RfColors.primary, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth().height((4 + 100 * count / maxCount).dp)) {}
                                Text("$channel", style = MaterialTheme.typography.labelSmall, color = RfColors.textDim)
                            }
                        }
                    }
                    Text("같은 채널의 AP 수이며 실제 트래픽·간섭량은 아닙니다.", style = MaterialTheme.typography.bodySmall, color = RfColors.textDim)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BandStat("전체 AP", rows.size, Modifier.weight(1f))
                BandStat("5 GHz", rows.count { it.freq in 5000..5895 }, Modifier.weight(1f))
                BandStat("6 GHz", rows.count { it.freq in 5935..7115 }, Modifier.weight(1f))
            }
        }
        item { Text("액세스 포인트 · ${rows.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
        if (rows.isEmpty()) item { EmptyState("Wi-Fi 결과를 기다리고 있습니다", "실제 탐색에는 정확한 위치 권한과 위치 서비스가 필요합니다. 시스템 제한으로 갱신이 지연될 수 있습니다.") }
        items(rows.sortedByDescending { it.rssi }, key = { it.bssid }) { ap ->
            Surface(color = RfColors.surface, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ap.ssid ?: "숨김 네트워크", color = if (ap.hidden) RfColors.warning else RfColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(channelLabel(ap.freq), color = RfColors.primary, style = MaterialTheme.typography.labelSmall)
                        Text(ap.bssid, color = RfColors.textDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${ap.rssi}\ndBm", color = RfColors.textDim, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun BandStat(label: String, count: Int, modifier: Modifier) {
    Surface(modifier = modifier, color = RfColors.surface, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = RfColors.textDim)
            Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

internal fun channelNumber(frequency: Int): Int? = when {
    frequency == 2484 -> 14
    frequency in 2412..2472 && (frequency - 2407) % 5 == 0 -> (frequency - 2407) / 5
    frequency == 5935 -> 2
    frequency in 5955..7115 && (frequency - 5950) % 5 == 0 -> (frequency - 5950) / 5
    frequency in 5000..5895 && frequency % 5 == 0 -> (frequency - 5000) / 5
    else -> null
}

internal fun channelLabel(frequency: Int): String {
    val band = when (frequency) { in 2400..2500 -> "2.4 GHz"; in 5000..5895 -> "5 GHz"; in 5935..7115 -> "6 GHz"; else -> "$frequency MHz" }
    return channelNumber(frequency)?.let { "$band · 채널 $it" } ?: band
}
