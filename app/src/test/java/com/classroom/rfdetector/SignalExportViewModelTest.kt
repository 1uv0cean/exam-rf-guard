package com.classroom.rfdetector

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.classroom.rfdetector.core.SignalSnapshot
import com.classroom.rfdetector.core.signalReport
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignalExportViewModelTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test fun restoredPendingFileCompletesWithoutItsOriginalComparisonCard() {
        val expected = "복원된 선택 시점의 전파 기록\nBLE | 원본 이름\n"
        val file = pendingFile(expected)
        val state = SavedStateHandle(mapOf("pendingFile" to file.absolutePath))
        val restored = SignalExportViewModel(app, state)
        val output = ByteArrayOutputStream()
        val uri = Uri.parse("content://rf-test/restored-report.txt")
        shadowOf(app.contentResolver).registerOutputStream(uri, output)
        assertTrue(restored.busy.value)
        assertNull(restored.launchFilename.value)

        restored.complete(uri)
        await { !restored.busy.value }

        assertEquals(expected, output.toString(Charsets.UTF_8.name()))
        assertEquals("전파 기록 파일을 저장했습니다.", restored.status.value)
        assertFalse(file.exists())
        assertNull(state.get<String>("pendingFile"))
        assertNull(state.get<String>("launchFilename"))
    }

    @Test fun cancellingRestoredPickerClearsSavedStateAndTemporaryFile() {
        val file = pendingFile("취소할 기록")
        val state = SavedStateHandle(mapOf("pendingFile" to file.absolutePath, "launchFilename" to "rf-record.txt"))
        val restored = SignalExportViewModel(app, state)
        restored.launched()
        assertNull(restored.launchFilename.value)
        assertTrue(restored.busy.value)

        restored.complete(null)
        await { !restored.busy.value }

        assertEquals("파일 저장을 취소했습니다.", restored.status.value)
        assertFalse(file.exists())
        assertNull(state.get<String>("pendingFile"))
        assertNull(state.get<String>("launchFilename"))
    }

    @Test fun failedDestinationAllowsPreparingAndSavingAnotherExport() {
        val file = pendingFile("첫 저장 시도")
        val state = SavedStateHandle(mapOf("pendingFile" to file.absolutePath))
        val restored = SignalExportViewModel(app, state)
        val failedUri = Uri.parse("content://rf-test/full-storage.txt")
        shadowOf(app.contentResolver).registerOutputStream(failedUri, object : OutputStream() {
            override fun write(value: Int) { throw IOException("Storage unavailable") }
        })
        restored.complete(failedUri)
        await { !restored.busy.value }
        assertEquals("파일 저장 실패 · 다시 시도하세요.", restored.status.value)
        assertFalse(file.exists())
        assertNull(state.get<String>("pendingFile"))

        val before = SignalSnapshot("before", "A실", "입실 전", 1000, 1000, true, emptyList())
        val after = before.copy(id = "after", label = "입실 후", capturedAtMs = 2000)
        restored.prepare(before, after, listOf(before, after))
        await { restored.launchFilename.value != null }
        val nextFile = File(requireNotNull(state.get<String>("pendingFile")))
        assertTrue(nextFile.exists())
        assertTrue(restored.busy.value)
        restored.launched()
        val output = ByteArrayOutputStream()
        val retryUri = Uri.parse("content://rf-test/retried-report.txt")
        shadowOf(app.contentResolver).registerOutputStream(retryUri, output)
        restored.complete(retryUri)
        await { !restored.busy.value }

        assertEquals(signalReport(before, after, listOf(before, after)), output.toString(Charsets.UTF_8.name()))
        assertEquals("전파 기록 파일을 저장했습니다.", restored.status.value)
        assertFalse(nextFile.exists())
        assertNull(state.get<String>("pendingFile"))
    }

    private fun pendingFile(content: String): File = File.createTempFile("rf-report-", ".txt", app.cacheDir).apply { writeText(content) }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 7_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        fail("Export operation did not complete")
    }
}
