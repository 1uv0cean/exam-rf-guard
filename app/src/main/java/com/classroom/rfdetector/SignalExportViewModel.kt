package com.classroom.rfdetector

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.classroom.rfdetector.core.SignalSnapshot
import com.classroom.rfdetector.core.signalReport
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Export outlives a comparison card/tab and restores the pending file after activity recreation. */
class SignalExportViewModel(application: Application, private val state: SavedStateHandle) : AndroidViewModel(application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val launchFilename = state.getStateFlow<String?>("launchFilename", null)
    val busy = MutableStateFlow(state.get<String>("pendingFile") != null)
    val status = MutableStateFlow<String?>(null)

    fun prepare(baseline: SignalSnapshot, target: SignalSnapshot, archive: List<SignalSnapshot>) {
        if (busy.value) return
        busy.value = true
        scope.launch {
            try {
                val path = withContext(Dispatchers.IO) {
                    val report = signalReport(baseline, target, archive)
                    File.createTempFile("rf-report-", ".txt", getApplication<Application>().cacheDir).apply { writeText(report, Charsets.UTF_8) }.absolutePath
                }
                state["pendingFile"] = path
                state["launchFilename"] = "RF-신호비교-${target.capturedAtMs}.txt"
            } catch (_: Exception) {
                status.value = "파일을 준비하지 못했습니다. 다시 시도하세요."
                busy.value = false
            }
        }
    }

    fun launched() { state["launchFilename"] = null }

    fun complete(uri: Uri?) {
        val path = state.get<String>("pendingFile")
        scope.launch {
            status.value = if (uri == null) "파일 저장을 취소했습니다." else try {
                withContext(Dispatchers.IO) {
                    File(requireNotNull(path)).inputStream().use { input ->
                        requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri)).use { input.copyTo(it) }
                    }
                }
                "전파 기록 파일을 저장했습니다."
            } catch (_: Exception) { "파일 저장 실패 · 다시 시도하세요." }
            withContext(Dispatchers.IO) { path?.let { File(it).delete() } }
            state["pendingFile"] = null
            state["launchFilename"] = null
            busy.value = false
        }
    }
}
