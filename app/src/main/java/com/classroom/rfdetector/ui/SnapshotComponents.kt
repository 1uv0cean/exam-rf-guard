package com.classroom.rfdetector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classroom.rfdetector.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SnapshotSteps(current: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("입실 전 저장", "입실 후 비교", "추가 장비 확인").forEachIndexed { index, title ->
            val step = index + 1
            val active = step == current
            Surface(color = if (active) MaterialTheme.colorScheme.secondaryContainer else RfColors.surface,
                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).semantics {
                    stateDescription = when { active -> "현재 단계"; step < current -> "완료"; else -> "다음 단계" }
                }) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (step < current) "$step ✓" else "$step", color = if (step <= current) RfColors.primary else RfColors.textDim,
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(title, color = if (active) RfColors.primary else RfColors.textDim,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun SignalSummary(signal: SnapshotSignal) {
    val audio = signal.audioNameMatch
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryField("유형", signal.equipmentType(), Modifier.weight(1f))
            SummaryField(if (audio != null) "제조사 후보" else "제조사·등록사", audio?.brand ?: signal.manufacturer, Modifier.weight(1f))
        }
        SummaryField("모델 후보", audio?.model ?: signal.modelHint ?: "미확인")
        HorizontalDivider(color = RfColors.grid)
        SummaryField("관측 시간", observationSpan(signal.firstSeenMs, signal.lastSeenMs, signal.observationCount))
        val endFormat = if (snapshotTime(signal.firstSeenMs).take(5) == snapshotTime(signal.lastSeenMs).take(5)) "HH:mm:ss" else "MM/dd HH:mm:ss"
        Text("${snapshotTime(signal.firstSeenMs)} → ${SimpleDateFormat(endFormat, Locale.getDefault()).format(Date(signal.lastSeenMs))}",
            color = RfColors.textDim, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SummaryField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = RfColors.textDim, style = MaterialTheme.typography.labelSmall)
        Text(value, color = RfColors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ExplanationToggle(title: String, expanded: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.semantics { stateDescription = if (expanded) "펼침" else "접힘" }) {
        Text("$title ${if (expanded) "▴" else "▾"}")
    }
}
