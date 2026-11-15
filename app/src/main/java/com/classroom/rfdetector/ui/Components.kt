package com.classroom.rfdetector.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classroom.rfdetector.core.AudioNameMatch
import com.classroom.rfdetector.core.WearableKind
import com.classroom.rfdetector.core.WearableNameMatch

@Composable
fun SectionIntro(eyebrow: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = RfColors.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = RfColors.textDim)
    }
}

@Composable
fun AudioIdentityPanel(audio: AudioNameMatch?, model: String?, evidenceNote: String? = null) {
    NameIdentityPanel(audio?.let { "${it.kind.label} · ${it.brand}" } ?: "모델 식별", model, evidenceNote)
}

@Composable
fun WearableIdentityPanel(wearable: WearableNameMatch) {
    NameIdentityPanel("${wearable.kind.label} · ${wearable.brand}", wearable.model,
        "이름은 변경·모방할 수 있습니다. 이 후보만으로 카메라 탑재, AI 사용, 소유자 또는 부정행위 여부를 판단하지 않습니다.")
}

internal fun WearableKind.symbol(): RfSymbol = when (this) {
    WearableKind.SMART_GLASSES -> RfSymbol.GLASSES
    WearableKind.SMARTWATCH -> RfSymbol.WATCH
    WearableKind.CAMERA -> RfSymbol.CAMERA
}

@Composable
private fun NameIdentityPanel(title: String, model: String?, evidenceNote: String?) {
    Surface(color = RfColors.surfaceAlt, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = RfColors.primary)
            Text("모델 후보: ${model ?: "확인 불가"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RfColors.textPrimary)
            Text(if (model != null) "광고 이름을 해석한 후보입니다. 세대·세부 모델은 이름에 명시된 범위만 표시합니다." else "이름이나 광고 데이터에 모델 정보가 없습니다. 제조사 코드만으로 모델을 추측하지 않습니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
            evidenceNote?.let { Text(it, color = RfColors.warning, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun EmptyState(title: String, description: String) {
    Surface(color = RfColors.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, RfColors.grid), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("◎", color = RfColors.primary, style = MaterialTheme.typography.headlineMedium)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}
