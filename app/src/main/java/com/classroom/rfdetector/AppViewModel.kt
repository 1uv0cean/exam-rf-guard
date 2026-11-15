package com.classroom.rfdetector

import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationManagerCompat
import com.classroom.rfdetector.core.DistanceClass
import com.classroom.rfdetector.core.DeviceIdentityResolver
import com.classroom.rfdetector.core.LocalizationEngine
import com.classroom.rfdetector.core.GeigerController
import com.classroom.rfdetector.core.MotionSensor
import com.classroom.rfdetector.core.DeviceTracker
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.RssiFilter
import com.classroom.rfdetector.core.TrackedDevice
import com.classroom.rfdetector.core.SignalPresence
import com.classroom.rfdetector.core.SignalSnapshot
import com.classroom.rfdetector.core.SnapshotStore
import com.classroom.rfdetector.core.ObservationUpdate
import com.classroom.rfdetector.core.ScanSession
import com.classroom.rfdetector.scanner.BleScanner
import com.classroom.rfdetector.scanner.WifiScanner
import com.classroom.rfdetector.sim.RadioSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/** 스캐너/추적/위치엔진/지예거를 묶는 상태 허브. Activity 와 1:1 로 붙는다 */
class AppViewModel(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val tracker = DeviceTracker()
    val engine = LocalizationEngine()
    val motion = MotionSensor(app)
    val geiger = GeigerController(app)
    val ble = BleScanner(app)
    val wifi = WifiScanner(app, scope)
    val sim = RadioSimulator()

    private val _devices = MutableStateFlow<List<TrackedDevice>>(emptyList())
    val devices: StateFlow<List<TrackedDevice>> = _devices

    val scanning = MutableStateFlow(false)
    val simMode = MutableStateFlow(false)
    val geigerOn = MutableStateFlow(false)
    val baselineSet = MutableStateFlow(false)
    val newCount = MutableStateFlow(0)
    val headingDeg = MutableStateFlow(0.0)
    val statusMsg = MutableStateFlow<String?>(null)
    val wifiStats = MutableStateFlow<List<ApRow>>(emptyList())
    val savedSnapshots = MutableStateFlow<List<SignalSnapshot>>(emptyList())
    val baselineSnapshot = MutableStateFlow<SignalSnapshot?>(null)
    val comparisonSnapshot = MutableStateFlow<SignalSnapshot?>(null)
    val snapshotBusy = MutableStateFlow(true)
    val snapshotError = MutableStateFlow<String?>(null)
    val latestNewSignal = MutableStateFlow<String?>(null)
    val areaDraft = MutableStateFlow("시험실")
    val scanSession = MutableStateFlow<ScanSession?>(null)
    private val snapshotStore = SnapshotStore(app.filesDir)
    private var snapshotStorageReady = false

    data class ApRow(
        val ssid: String?,
        val bssid: String,
        val rssi: Int,
        val freq: Int,
        val hidden: Boolean,
    )

    private var simJob: Job? = null
    private var refreshJob: Job? = null
    private var identityResolver = DeviceIdentityResolver(emptyMap(), emptyMap())

    init {
        scope.launch {
            try {
                savedSnapshots.value = withContext(Dispatchers.IO) { snapshotStore.load() }
                snapshotStorageReady = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snapshotError.value = "저장 자료를 불러오지 못했습니다. 기존 파일은 변경하지 않습니다."
            } finally { snapshotBusy.value = false }
        }
        scope.launch {
            identityResolver = withContext(Dispatchers.IO) {
                fun assetRows(file: String) = app.assets.open(file).bufferedReader().useLines { lines ->
                    lines.filter { it.contains('\t') }.associate { line -> line.substringBefore('\t') to line.substringAfter('\t') }
                }
                DeviceIdentityResolver(assetRows("mac-vendors.tsv"), assetRows("bluetooth-companies.tsv").mapKeys { it.key.toInt() })
            }
        }
        if (!ble.hasHardware()) {
            simMode.value = true
            statusMsg.value = "이 기기에는 블루투스가 없어 시뮬레이션 모드로 시작합니다"
        }
        motion.onStep = {
            if (!simMode.value && scanning.value) engine.moveAll(0.0, motion.stepLengthM)
        }
        motion.onHeadingDelta = { d ->
            if (!simMode.value && scanning.value) engine.rotateAll(d)
        }
        wifi.onError = { msg -> statusMsg.value = msg }
        ble.onError = { msg -> statusMsg.value = msg }
    }

    /** API 레벨별로 런타임 요청이 필요한 권한 목록 */
    fun requiredPermissions(): List<String> {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            p += "android.permission.BLUETOOTH_SCAN"
            p += "android.permission.BLUETOOTH_CONNECT"
        }
        p += "android.permission.ACCESS_COARSE_LOCATION"
        p += "android.permission.ACCESS_FINE_LOCATION"
        if (Build.VERSION.SDK_INT >= 33) p += "android.permission.NEARBY_WIFI_DEVICES"
        return p
    }

    /** WifiManager 스캔 결과에는 위치 서비스가 필요하다. */
    fun locationServicesOff(): Boolean {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return !LocationManagerCompat.isLocationEnabled(lm)
    }

    fun startScan() {
        if (scanning.value) return
        tracker.clear()
        engine.clear()
        geiger.setClass(DistanceClass.UNKNOWN)
        _devices.value = emptyList()
        wifiStats.value = emptyList()
        newCount.value = 0
        statusMsg.value = null
        scanSession.value = ScanSession(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), areaDraft.value.trim().ifBlank { "시험실" })
        applyBaselineToSession()
        latestNewSignal.value = null
        scanning.value = true
        if (simMode.value) {
            sim.reset()
            simJob = scope.launch {
                while (isActive) {
                    sim.tick().forEach { onObservation(it) }
                    delay(250)
                }
            }
            statusMsg.value = "시뮬레이션 모드: 가상 기기 5대 배치 — 조이스틱으로 걸어보세요"
        } else {
            if (!ble.isUsable()) {
                statusMsg.value = "블루투스 꺼짐/없음 — BLE 스캔 생략"
            } else if (!ble.start(::onObservation)) {
                statusMsg.value = "BLE 스캔 시작 실패(권한 확인)"
            }
            if (locationServicesOff()) {
                statusMsg.value = "위치 서비스가 꺼져 있어 Wi-Fi 결과가 안 보일 수 있음"
            }
            wifi.start(::onObservation)
        }
        motion.start()
        refreshJob = scope.launch {
            while (isActive) {
                refresh()
                delay(100)
            }
        }
    }

    private fun onObservation(obs: RfObservation) {
        if (!scanning.value) return
        val update = tracker.update(obs)
        if (update == ObservationUpdate.FIRST_SEEN && baselineSet.value) {
            if (obs.trackingKey !in (tracker.baselineKeys ?: emptySet())) latestNewSignal.value = obs.name ?: obs.key
        }
        if (update != ObservationUpdate.LATE) obs.rssi?.let { r ->
            val tx = obs.txPower?.toDouble() ?: RssiFilter.DEFAULT_TX_DBM
            engine.deviceFilter(obs.trackingKey).observeRssi(r.toDouble(), tx)
        }
    }

    fun stopScan() {
        if (!scanning.value) return
        refresh()
        scanning.value = false
        simJob?.cancel(); simJob = null
        refreshJob?.cancel(); refreshJob = null
        ble.stop()
        wifi.stop()
        motion.stop()
        if (geigerOn.value) setGeiger(false)
    }

    fun takeBaseline() {
        if (!scanning.value || devices.value.isEmpty()) return
        saveSnapshot(scanSession.value?.area ?: areaDraft.value, "기준점", asBaseline = true)
    }

    fun updateAreaDraft(value: String) {
        areaDraft.value = value.take(80)
        val session = scanSession.value ?: return
        if (!scanning.value || session.areaLocked || value.isBlank()) return
        scanSession.value = session.copy(area = areaDraft.value.trim())
        applyBaselineToSession()
        latestNewSignal.value = null
        refresh()
    }

    private fun applyBaselineToSession() {
        val baseline = baselineSnapshot.value
        val session = scanSession.value
        val matches = baseline != null && session != null && baseline.simulation == simMode.value && baseline.area.equals(session.area, true)
        baselineSet.value = matches
        if (matches) tracker.setBaseline(baseline!!.signals.map { it.matchKey }.toSet()) else tracker.clearBaseline()
    }

    fun saveSnapshot(area: String, label: String, asBaseline: Boolean = false) {
        if (!scanning.value || snapshotBusy.value || !snapshotStorageReady) return
        if (area.isBlank() || label.isBlank()) { snapshotError.value = "공간 이름과 시점 이름을 입력해 주세요."; return }
        val current = scanSession.value ?: return
        val requestedArea = area.trim().take(80)
        if (current.areaLocked && !current.area.equals(requestedArea, true)) {
            snapshotError.value = "현재 탐색 공간은 ${current.area}입니다. 다른 공간을 기록하려면 탐색을 중지하고 새로 시작하세요."
            return
        }
        val session = current.copy(area = requestedArea, areaLocked = true)
        scanSession.value = session
        areaDraft.value = requestedArea
        applyBaselineToSession()
        refresh()
        val snapshot = SignalSnapshot.capture(java.util.UUID.randomUUID().toString(), area.trim().take(80), label.trim().take(80),
            System.currentTimeMillis(), session.startedAtMs, simMode.value, devices.value,
            includeNewHistorySinceMs = if (asBaseline || !baselineSet.value) null else baselineSnapshot.value?.capturedAtMs)
        val updated = listOf(snapshot) + savedSnapshots.value
        snapshotBusy.value = true
        snapshotError.value = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { snapshotStore.save(updated) }
                savedSnapshots.value = updated
                if (scanSession.value?.id == session.id && snapshot.simulation == simMode.value) {
                    if (asBaseline) selectBaseline(snapshot) else comparisonSnapshot.value = snapshot
                }
                statusMsg.value = "스냅샷 저장됨 · ${snapshot.area} · ${snapshot.label} · ${snapshot.signals.size}개 신호"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snapshotError.value = "스냅샷을 저장하지 못했습니다. 기존 자료는 유지됩니다."
            } finally { snapshotBusy.value = false }
        }
    }

    fun selectBaseline(snapshot: SignalSnapshot) {
        if (snapshot.simulation != simMode.value) { snapshotError.value = "현재 탐색 모드와 같은 자료를 선택해 주세요."; return }
        baselineSnapshot.value = snapshot
        areaDraft.value = snapshot.area
        val session = scanSession.value
        if (scanning.value && session != null && !session.areaLocked) scanSession.value = session.copy(area = snapshot.area)
        applyBaselineToSession()
        snapshotError.value = if (scanSession.value?.area?.equals(snapshot.area, true) == false)
            "저장 기준은 ${snapshot.area}, 현재 수신 기록은 ${scanSession.value!!.area}입니다. 저장 자료끼리 비교하거나 새 공간에서 탐색을 시작하세요." else null
        latestNewSignal.value = null
        refresh()
    }

    fun setGeiger(on: Boolean) {
        if (on && !scanning.value) return
        geigerOn.value = on
        if (on) {
            geiger.start()
        } else {
            geiger.stop()
        }
    }

    fun setSim(on: Boolean) {
        if (simMode.value == on) return
        if (scanning.value) stopScan()
        simMode.value = on
        tracker.clear()
        engine.clear()
        _devices.value = emptyList()
        wifiStats.value = emptyList()
        baselineSet.value = false
        baselineSnapshot.value = null
        comparisonSnapshot.value = null
        scanSession.value = null
        latestNewSignal.value = null
        newCount.value = 0
        statusMsg.value = if (on) "연습 모드 · 가상 기기로 탐색 흐름을 체험하세요" else "실제 탐색 · BLE와 Wi-Fi 신호를 확인합니다"
    }

    /** 시뮬레이터 조이스틱: 정면/우측 미터 단위 이동 */
    fun simMove(forward: Double, right: Double) {
        if (!scanning.value || !simMode.value) return
        sim.moveUser(right, forward)
        engine.moveAll(right, forward)
    }

    fun simRotate(deltaDeg: Double) {
        if (!scanning.value || !simMode.value) return
        sim.rotateUser(deltaDeg)
        engine.rotateAll(deltaDeg)
    }

    fun simAddDevice() {
        if (scanning.value && simMode.value) sim.addDevice()
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val list = tracker.snapshot(now, includeInactive = true).map { d ->
            val est = if (d.presence == SignalPresence.RECENT) engine.deviceFilter(d.trackingKey).estimate() else null
            if (d.presence == SignalPresence.HISTORY) engine.remove(d.trackingKey)
            d.copy(
                bearingDeg = est?.first,
                bearingConfidence = est?.second ?: 0.0,
                identity = identityResolver.resolve(d.key, d.displayName, setOf(d.source), d.manufacturerIds),
            )
        }
        devices.value.filter { old -> list.none { it.trackingKey == old.trackingKey } }.forEach { engine.remove(it.trackingKey) }
        _devices.value = list
        newCount.value = list.count { it.isBaselineNew }
        headingDeg.value = if (simMode.value) sim.userHeadingDeg else motion.headingDeg
        geiger.setClass(
            list.filter { it.distanceM != null && it.presence == SignalPresence.RECENT }
                .minByOrNull { it.distanceM!! }
                ?.distanceClass ?: DistanceClass.UNKNOWN
        )
        wifiStats.value = if (simMode.value) list.filter { it.source == com.classroom.rfdetector.core.ScanSource.WIFI }.map { device ->
            val frequency = sim.devices.first { it.key == device.key }.frequencyMhz!!
            ApRow(if (device.hidden) null else device.displayName, device.key, device.rssiEma?.toInt() ?: -100, frequency, device.hidden)
        } else wifi.lastResults.filter { android.os.SystemClock.elapsedRealtime() - it.timestamp / 1000 in 0..90_000 }.map {
            ApRow(
                ssid = it.SSID?.takeIf { s -> s.isNotBlank() },
                bssid = it.BSSID,
                rssi = it.level,
                freq = it.frequency,
                hidden = it.SSID.isNullOrBlank(),
            )
        }
    }

    fun shutdown() {
        stopScan()
        scope.cancel()
    }
}
