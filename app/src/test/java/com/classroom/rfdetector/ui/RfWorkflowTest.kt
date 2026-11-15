package com.classroom.rfdetector.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.classroom.rfdetector.App
import com.classroom.rfdetector.AppViewModel
import com.classroom.rfdetector.SignalExportViewModel
import com.classroom.rfdetector.core.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RfWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun receptionStatusShowsRadioCountsAndAutomaticPreservation() {
        compose.setContent { AppRfTheme { App() } }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        try {
            compose.onNodeWithText("연습 모드").performClick()
            compose.onNodeWithText("◎  탐색 시작").performClick()
            compose.waitUntil(10_000) { vm.devices.value.size == 5 }
            compose.onNodeWithContentDescription("수신 상태").performClick()
            compose.onNodeWithText("연습 데이터 · 실제 무선 수신이 아닙니다").assertExists()
            compose.onNodeWithText("한 번 이상 포착").assertExists()
            saveScreen("rf-reception-status")
            scrollToText("BLE · 가상 신호")
            compose.onNodeWithText("총 3개 주소").assertExists()
            scrollToText("Wi-Fi · 가상 신호")
            compose.onNodeWithText("총 2개 주소").assertExists()
            compose.runOnIdle { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6)) }
            compose.waitUntil(10_000) { vm.checkpoints.value.any { it.id == "auto-${vm.scanSession.value?.id}" } }
            scrollToText("5개 주소 보존됨")
            saveScreen("rf-auto-preserved")
            compose.onNodeWithText("■  탐색 중지").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                !vm.checkpointBusy.value
            }
            val saved = CheckpointStore(File(compose.activity.filesDir, "checkpoints")).load()
            assertEquals(5, saved.first { it.id == "auto-${vm.scanSession.value?.id}" }.signals.size)
            scrollToText("① 입실 전 저장으로 이동")
            compose.onNodeWithText("① 입실 전 저장으로 이동").performClick()
            compose.onNodeWithContentDescription("비교").assertIsSelected()
        } finally { vm.shutdown() }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun receptionStatusControlsRemainReachableAtLargeText() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) { AppRfTheme { App() } }
        }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        try {
            compose.onNodeWithText("연습 모드").performClick()
            compose.onNodeWithText("◎  탐색 시작").performClick()
            compose.waitUntil(10_000) { vm.devices.value.size == 5 }
            compose.onNodeWithContentDescription("수신 상태").performClick()
            scrollToText("Wi-Fi · 가상 신호")
            compose.onNodeWithText("■  탐색 중지").assertIsDisplayed()
            saveScreen("rf-status-small-large-text")
            scrollToText("수신 상태·감지 범위 설명 ▾")
            compose.onNodeWithText("수신 상태·감지 범위 설명 ▾").performClick()
            scrollToText("수신 상태·감지 범위 설명 ▴")
            compose.onNodeWithText("수신 상태·감지 범위 설명 ▴").performClick()
            scrollToText("포착한 신호 전체 보기")
            compose.onNodeWithText("포착한 신호 전체 보기").performClick()
            compose.onNodeWithContentDescription("기기 5").assertIsSelected()
            compose.onNodeWithContentDescription("수신 상태").performClick()
            scrollToText("① 입실 전 저장으로 이동")
            compose.onNodeWithText("① 입실 전 저장으로 이동").performClick()
            compose.onNodeWithContentDescription("비교").assertIsSelected()
            scrollToText("① 입실 전 저장")
            compose.onNodeWithText("① 입실 전 저장").assertIsDisplayed()
            compose.onNodeWithText("■  탐색 중지").assertIsDisplayed().performClick()
        } finally { vm.shutdown() }
    }

    @Test fun addedSmartGlassesShowCandidateIdentityPeriodsAndSourceEvidence() {
        val vm = AppViewModel(compose.activity.applicationContext)
        try {
            compose.setContent { AppRfTheme { Surface(color = RfColors.bg) { SnapshotScreen(vm) } } }
            compose.waitUntil(10_000) { !vm.snapshotBusy.value }
            val (before, after) = glassesComparison()
            compose.runOnIdle {
                vm.setSim(true)
                vm.baselineSnapshot.value = before
                vm.comparisonSnapshot.value = after
                vm.savedSnapshots.value = listOf(before, after)
            }
            scrollToText("Ray-Ban Meta (Gen 2)")
            compose.onNodeWithText("스마트 안경 후보").assertExists()
            compose.onNodeWithText("Ray-Ban / Meta").assertExists()
            compose.onNodeWithContentDescription("스마트 안경").assertExists()
            compose.onNodeWithText("2개 관측 구간 · 재등장 1회").assertExists()
            saveScreen("rf-glasses-added")
            compose.onNodeWithText("Ray-Ban Meta (Gen 2)").performClick()
            saveScreen("rf-glasses-detail")
            compose.onNodeWithText("관측 구간·재등장 보기 ▾").performScrollTo().performClick()
            compose.onNodeWithText("재등장 1").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("이전 구간 후 1분 59초 미수신").assertExists()
            compose.onNodeWithText("관측 구간·재등장 보기 ▴").performScrollTo().performClick()
            compose.onNodeWithText("식별 근거·시간 해석 보기 ▾").performScrollTo().performClick()
            compose.onNodeWithText("스마트 안경 후보 · Ray-Ban / Meta").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("모델 후보: Ray-Ban Meta (Gen 2)").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("이름은 변경·모방할 수 있습니다.", substring = true).performScrollTo().assertIsDisplayed()
            saveScreen("rf-glasses-evidence")
            compose.onNodeWithText("닫기").assertIsDisplayed().performClick()
            scrollToText("전체 이력 2")
            compose.onNodeWithText("전체 이력 2").performClick()
            compose.onNodeWithText("기준~비교 사이 등장 주소의 합집합 · 중복 제외").assertExists()
        } finally { vm.shutdown() }
    }

    @Test fun exportedFileUsesFrozenComparisonAndCancelAllowsRetry() {
        val (before, after) = glassesComparison()
        val currentTarget = mutableStateOf(after)
        val exportVisible = mutableStateOf(true)
        compose.setContent {
            AppRfTheme { SignalExportHost { Surface(color = RfColors.bg) { Column(Modifier.padding(20.dp)) {
                if (exportVisible.value) SignalExportButton(before, currentTarget.value, listOf(before, currentTarget.value))
            } } } }
        }
        compose.onNodeWithText("전파 기록 파일로 저장").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { shadowOf(compose.activity).peekNextStartedActivityForResult() != null }
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
        assertEquals("text/plain", request.intent.type)
        val output = ByteArrayOutputStream()
        val uri = Uri.parse("content://rf-test/reports/comparison.txt")
        shadowOf(compose.activity.contentResolver).registerOutputStream(uri, output)
        compose.runOnIdle {
            currentTarget.value = after.copy(id = "later-result", signals = emptyList())
            exportVisible.value = false
        }
        compose.onNodeWithText("전파 기록 파일로 저장").assertDoesNotExist()
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri)) }
        val model = ViewModelProvider(compose.activity)[SignalExportViewModel::class.java]
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            !model.busy.value && model.status.value == "전파 기록 파일을 저장했습니다."
        }
        val report = output.toString(Charsets.UTF_8.name())
        assertEquals(signalReport(before, after, listOf(before, after)), report)
        assertTrue(report.contains("스마트 안경 후보"))
        assertTrue(report.contains("관측 합집합 · 2개 주소"))
        assertTrue(report.contains("재등장 1회"))
        File("build/outputs/ui-captures/rf-signal-report.txt").apply { parentFile?.mkdirs(); writeText(report) }
        compose.runOnIdle { exportVisible.value = true }
        compose.onNodeWithText("전파 기록 파일을 저장했습니다.").assertExists()
        compose.onNodeWithText("전파 기록 파일로 저장").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { shadowOf(compose.activity).peekNextStartedActivityForResult() != null }
        val cancelled = shadowOf(compose.activity).nextStartedActivityForResult
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(cancelled.intent, Activity.RESULT_CANCELED, null) }
        waitForText("파일 저장을 취소했습니다.")
        compose.onNodeWithText("전파 기록 파일로 저장").assertIsEnabled()
        assertTrue(compose.activity.cacheDir.listFiles().orEmpty().none { it.name.startsWith("rf-report-") })
    }

    /** Deterministic RF observations, not physical reception or product authentication. */
    private fun glassesComparison(): Pair<SignalSnapshot, SignalSnapshot> {
        val start = System.currentTimeMillis() - 180_000
        val tracker = DeviceTracker()
        val resolver = DeviceIdentityResolver(emptyMap(), emptyMap())
        fun observe(address: String, name: String, source: ScanSource, offset: Long) {
            tracker.update(RfObservation(address, source, name, -63, null, false, start + offset))
        }
        fun capture(label: String, offset: Long) = SignalSnapshot.capture(label, "제1시험실", label,
            start + offset, start, true, tracker.snapshot(start + offset, true).map {
                it.copy(identity = resolver.resolve(it.key, it.displayName, setOf(it.source), it.manufacturerIds))
            }, includeAllHistory = true)
        observe("room-ap", "시험장 AP", ScanSource.WIFI, 0)
        val before = capture("입실 전", 0)
        tracker.setBaseline(before.signals.map { it.matchKey }.toSet())
        observe("glasses-advertisement", "Ray-Ban Meta (Gen 2)", ScanSource.BLE, 1000)
        observe("glasses-advertisement", "Ray-Ban Meta (Gen 2)", ScanSource.BLE, 2000)
        observe("glasses-advertisement", "Ray-Ban Meta (Gen 2)", ScanSource.BLE, 121000)
        return before to capture("입실 후", 122000)
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun scrollToText(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun saveScreen(name: String) {
        val target = File("build/outputs/ui-captures/$name.png")
        target.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().lastOrNull() ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
