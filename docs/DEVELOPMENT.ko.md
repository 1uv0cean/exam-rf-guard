# 빌드·구조·검증 안내

일반 사용자는 [README](../README.md)와 [화면 사용법](QUICKSTART.ko.md)을 먼저 참고하세요. 이 문서는 v1.2.0-preview.1 구현을 확인하거나 수정하는 개발자를 위한 설명입니다.

## 빌드 환경

실제 검증에는 JDK 21을 사용했습니다. Android SDK Platform 35와 SDK Build Tools가 필요합니다. 컴파일·대상 SDK는 35, 최소 지원 SDK는 29(Android 10)입니다.

프로젝트의 Gradle wrapper는 9.7.1, Android Gradle Plugin은 8.7.3, Kotlin은 2.0.21, Compose BOM은 2024.12.01입니다. 고정값은 [버전 카탈로그](../gradle/libs.versions.toml)와 [wrapper 설정](../gradle/wrapper/gradle-wrapper.properties)을 기준으로 확인하세요. 다른 도구 조합의 호환성을 검증했다는 의미는 아닙니다.

1. 저장소를 복제하고 프로젝트 폴더로 이동합니다.

```shell
git clone https://github.com/junseokandylee/exam-rf-guard.git
cd exam-rf-guard
```

2. Android Studio에서 프로젝트를 열거나 SDK를 별도로 설치합니다. Android Studio의 Gradle JDK 또는 터미널의 `JAVA_HOME`을 설치한 JDK 21 경로로 설정합니다.
3. 프로젝트 루트의 `local.properties`에 자신의 SDK 경로를 지정합니다. Android Studio가 생성할 수도 있습니다. 이 파일은 Git에서 제외합니다.

```properties
# 예시이며 실제 설치 경로로 바꾸세요.
sdk.dir=C:/Android/Sdk
```

4. Windows PowerShell에서 검증과 빌드를 실행합니다.

```powershell
./gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

macOS/Linux에서는 `./gradlew testDebugUnitTest assembleDebug lintDebug`를 사용합니다. 최초 빌드에는 Gradle·의존성·테스트 런타임 다운로드를 위한 네트워크가 필요합니다. 앱의 오프라인 관측 동작과 개발용 다운로드는 별개입니다.

| 결과 | 경로 |
| --- | --- |
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 단위·UI 테스트 보고서 | `app/build/reports/tests/testDebugUnitTest/index.html` |
| Lint 보고서 | `app/build/reports/lint-results-debug.html` |
| 테스트에서 생성한 화면 | `app/build/outputs/ui-captures/` |

자체 빌드의 debug 서명은 개발 환경에 따라 달라집니다. 공개 릴리즈 APK 위에 바로 업데이트 설치되지 않을 수 있습니다. 저장 파일 v2를 읽지 못하는 이전 앱으로의 다운그레이드는 지원하지 않습니다.

## 주요 구성

| 구성 | 역할 |
| --- | --- |
| `MainActivity`, `AppViewModel` | 화면·권한·수신 생명주기, 세션과 비교 상태, 저장 조정 |
| `scanner/BleScanner` | BLE LOW_LATENCY, ALL_MATCHES, 보고 지연 0, 실제 패킷 시각 검사 |
| `scanner/WifiScanner` | 30초 간격 요청, 완료 브로드캐스트, 새 세션·중복·오래된 결과 필터 |
| `core/DeviceTracker`, `ObservationPeriods` | 무선 종류+주소별 누적 관측, 구간·재등장, 신호 상태 |
| `core/SignalSnapshot`, `SnapshotStore` | 수동 시점 기록·비교, 버전 있는 JSON 저장 |
| `core/CheckpointStore` | 세션별 누적 자동 보존, 디스크 최신본 병합, 오래된 저장 방지 |
| `core/DeviceIdentity`, `AudioNameCatalog`, `WearableNameCatalog` | 공식 회사 코드와 이름 기반 후보 해석 |
| `core/DistanceEstimate`, `LocalizationEngine` | 가정에 기반한 거리·이동 기반 방향 추정 |
| `core/SignalReport`, `SignalExportViewModel` | 고정된 비교 내용의 텍스트 보고서와 시스템 파일 저장 |
| `ui/SnapshotScreen`, `DeviceListScreen`, `ScanStatusScreen` | 저장→비교→추가 신호 흐름, 상세와 수신 상태 |

## 수신과 저장의 계약

- `radioKey`는 무선 종류와 대문자로 정규화한 주소의 조합입니다. BLE와 Wi-Fi는 동일 주소 문자열이어도 별도 관측입니다.
- 콜백에서 추적기에 즉시 기록하고 화면용 집계는 100ms마다 갱신합니다. 빠른 화면 갱신을 무선 탐지율 보장으로 해석하지 않습니다.
- BLE는 탐색 시작 이전·미래·0/음수 타임스탬프를 제외합니다. 현재 탐색 안에서 늦게 도착한 패킷은 실제 관측 시각을 유지합니다.
- Wi-Fi는 성공한 완료 브로드캐스트에서 새 세션 시작 이후의 관측만 받습니다. 같은 주소의 같거나 이전 타임스탬프를 중복 수신 횟수로 세지 않습니다.
- `HISTORY` 주소도 세션 전체 목록에는 유지합니다. 수동 기준은 전체 세션 이력, 비교는 최근 수신과 기준 이후 새 주소의 이력을 포함합니다.
- BLE 15초·Wi-Fi 90초 초과 공백을 관측 구간 경계로 삼습니다. 늦은 관측이 공백을 메우면 구간을 합칠 수 있습니다.
- 자동 보존은 5초마다·정상 중지 시 하나의 세션 기록을 갱신합니다. 수동 스냅샷은 불변입니다. 저장 실패와 강제 종료 직전 유실 가능성은 UI·사용자 안내에 명시합니다.
- 공간은 첫 수동·자동 저장에서 고정합니다. 다른 공간은 새 탐색으로 수집합니다.
- `ON_STOP`에서 수신을 중지하고 `ON_START`에서 같은 세션으로 재개합니다(공백 횟수·시간은 `ScanSession`에 기록). 사용자가 직접 중지한 탐색은 재개하지 않습니다. ViewModel은 Activity 재생성에 유지되고 요청된 저장은 화면 수명과 분리해 마칩니다. 백그라운드 수신 서비스는 없습니다.

자료는 앱 내부 JSON으로 저장하며 임시 파일 쓰기·동기화 후 교체합니다. 스냅샷 v2는 관측 구간·1m 기준 필드를 포함하고 v1을 읽습니다. 구간 정보가 없으면 재등장을 만들어내지 않습니다. 지원하지 않는 파일이나 손상 파일을 불러오지 못했을 때 기존 자료를 빈 목록으로 덮어쓰지 않도록 호출 경로를 보호합니다.

## 거리 모델

EMA RSSI와 아래 모델을 사용합니다.

```text
d = 10 ^ ((A - RSSI) / (10 × n))
n = 2.7
A = 기기에서 받은 유효한 1m RSSI 기준, 없으면 −59 dBm 가정
```

실제 BLE 스캐너는 일반 광고의 AD Tx Power를 1m 보정 RSSI로 사용하지 않습니다. 현재 실제 BLE·Wi-Fi 수신 경로는 1m 기준값을 추출하지 않아 모두 미보정 가정값을 사용합니다. 데이터 모델에는 기준값을 담을 수 있지만 현재 앱에 사용자 보정 기능은 없습니다. 재등장 시 EMA를 새 구간부터 계산합니다.

상세 범위는 RSSI ±6 dB, n=2~4, 미보정 A ±10 dB 조합의 최소~최대입니다. 통계적 신뢰구간이나 거리 정확도 보장이 아닙니다. 근거 해석은 [Bluetooth SIG의 RSSI 설명](https://www.bluetooth.com/blog/proximity-and-rssi/)을 참고하세요.

## 검증과 남은 한계

2026년 11월 16일 v1.2.0-preview.1 기준 116개 테스트, `assembleDebug`, `lintDebug`가 통과했습니다. Lint는 오류 0개·경고 21개입니다. 테스트의 전체 목록은 [test 소스](../app/src/test/java/com/classroom/rfdetector)에 있습니다.

관측·비교·보존·식별·스캐너 콜백·Android UI·내보내기를 검증합니다. 화면 캡처에는 연습 모드나 결정적인 테스트 입력을 사용합니다. 실제 무선 수신 환경, 시스템 권한 대화상자의 단말별 차이, 센서 정확도, 발열·배터리를 대신 검증하지 않습니다.

실기기 검증 결과를 제안할 때는 단말·OS·앱 버전, 기기명과 동작 상태, 측정 조건, 수신/미수신 시각, 반복 관측을 구분해 기록해 주세요. 개인 이름이나 실제 현장의 식별 자료를 그대로 공개하지 마세요.

## 공개 자료 관리

`local.properties`, `.env`, 서명 키, 빌드 출력, 실제 현장 수신 기록을 커밋하지 않습니다. 테스트 fixture와 예시 화면은 실측 자료와 구분합니다. 일반 사용자에게 노출되는 설명에는 근거 없는 탐지율·AI 판정·정확한 위치 보장을 추가하지 않습니다.

현재 별도 오픈소스 라이선스는 미지정입니다. 외부 코드·데이터의 조건과 출처를 확인하고, 프로젝트의 배포·재사용 조건은 [README의 라이선스 상태](../README.md#sources)를 참고하세요.
