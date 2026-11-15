package com.classroom.rfdetector.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.classroom.rfdetector.SignalExportViewModel
import com.classroom.rfdetector.core.SignalSnapshot

@Composable
private fun exportModel(): SignalExportViewModel {
    val activity = LocalContext.current as ComponentActivity
    return remember(activity) { ViewModelProvider(activity)[SignalExportViewModel::class.java] }
}

/** Always composed at app level so file-picker results survive tabs, collapsed cards and recreation. */
@Composable
internal fun SignalExportHost(content: @Composable () -> Unit) {
    val model = exportModel()
    val filename by model.launchFilename.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), model::complete)
    LaunchedEffect(filename) {
        filename?.let { launcher.launch(it); model.launched() }
    }
    content()
}

@Composable
internal fun SignalExportButton(baseline: SignalSnapshot, target: SignalSnapshot, archive: List<SignalSnapshot>) {
    val model = exportModel()
    val busy by model.busy.collectAsState()
    val status by model.status.collectAsState()
    OutlinedButton(onClick = { model.prepare(baseline, target, archive) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        RfGlyph(RfSymbol.SNAPSHOT); Spacer(Modifier.width(8.dp)); Text("전파 기록 파일로 저장")
    }
    if (busy) Text("선택한 기록 파일 저장 중", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
    status?.let { Text(it, color = RfColors.textDim, style = MaterialTheme.typography.bodySmall) }
}
