# ToggleFullChargeForGalaxy (이번만 완전 충전)

**한국어** | [English](README.en.md)

> 이 앱은 Anthropic의 AI 어시스턴트인 Claude를 이용해 만들었습니다. 코드 작성, 빌드, 문서화를 Claude와 함께 진행했습니다.

Mac의 "지금 완전히 충전" 기능을 갤럭시에서 흉내 내는 앱입니다. 위젯을 누르면 One UI 배터리 보호(최대 80%)를 잠시 끄고, 완충되거나 설정한 시간이 지나면 원래 제한으로 되돌립니다.

- 루트 권한은 필요하지 않습니다. 대신 `WRITE_SECURE_SETTINGS` 권한을 adb로 한 번 부여해야 합니다.
- 테스트 완료: 갤럭시 S23, S26 (One UI 9)
- 테스트 예정: 갤럭시 Tab S8+ (One UI 8)

## 설치

PC에서 adb로 앱을 설치하고 권한을 부여합니다. 아래 설명은 Windows 명령 프롬프트(CMD)를 기준으로 하며, 예시의 IP 주소·포트·페어링 코드는 모두 가상의 값입니다. 실제로는 휴대폰 화면에 표시되는 값을 입력해 주세요.

### 1. 준비물 내려받기

1. [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)를 내려받아 원하는 위치에 압축을 풉니다. 예: `C:\platform-tools`
2. [Releases](../../releases)에서 `ToggleFullChargeForGalaxy-v<버전>.apk`를 내려받아 같은 폴더에 넣습니다.
3. 해당 폴더에서 명령 프롬프트를 엽니다. 탐색기 주소창에 `cmd`를 입력하고 Enter를 누르면 그 폴더에서 바로 열립니다.

### 2. 휴대폰에서 무선 디버깅 켜기

1. 설정 → 휴대전화 정보 → 소프트웨어 정보 → **빌드번호**를 7번 누릅니다. "개발자 모드를 켰습니다"라는 안내가 나오면 됩니다.
2. 설정 → 개발자 옵션 → **무선 디버깅**을 켭니다.
3. PC와 휴대폰이 **같은 Wi-Fi**에 연결되어 있어야 합니다.

### 3. 페어링 (기기마다 처음 한 번만)

휴대폰에서 무선 디버깅 → **페어링 코드로 기기 페어링**을 누르면 아래와 같은 화면이 나옵니다.

```
Wi-Fi 페어링 코드:  123456
IP 주소 및 포트:     192.168.xxx.xxx:xxxxx
```

PC에서 표시된 IP 주소와 포트로 페어링한 뒤, 6자리 코드를 입력합니다.

```
adb pair 192.168.xxx.xxx:xxxxx
Enter pairing code: 123456
Successfully paired to 192.168.xxx.xxx:xxxxx
```

### 4. 연결

페어링 창을 닫으면 무선 디버깅 첫 화면에 **IP 주소 및 포트**가 따로 표시됩니다. 이 포트는 페어링 포트와 다릅니다.

```
IP 주소 및 포트:     192.168.xxx.xxx:xxxxx
```

이 주소로 연결하고, 목록에 기기가 나오는지 확인합니다.

```
adb connect 192.168.xxx.xxx:xxxxx
connected to 192.168.xxx.xxx:xxxxx

adb devices
List of devices attached
192.168.xxx.xxx:xxxxx      device
```

### 5. 호환성 확인

```
adb shell getprop ro.build.version.oneui
90000

adb shell settings get global protect_battery
1
```

첫 번째 결과는 One UI 버전입니다(80000은 One UI 8, 90000은 One UI 9).

두 번째 결과로 `0`, `1`, `3`, `4` 중 하나가 나오면 사용할 수 있습니다. `null`이 나오면 해당 기기에는 이 설정이 없어 앱이 동작하지 않습니다.

### 6. 설치와 권한 부여

```
adb install -r ToggleFullChargeForGalaxy-v1.4.1.apk
Performing Streamed Install
Success

adb shell pm grant app.togglefullcharge android.permission.WRITE_SECURE_SETTINGS
```

권한 부여 명령은 성공하면 아무것도 출력하지 않습니다. 앱을 열어 "권한: 부여됨"으로 표시되는지 확인한 뒤, 홈 화면에 위젯을 추가하면 됩니다.

### 참고

- **재부팅하거나 Wi-Fi가 바뀐 경우**: 무선 디버깅이 꺼집니다. 다시 켜면 연결 포트가 바뀌므로 화면에 새로 표시된 주소로 4번 연결부터 다시 하면 됩니다. 페어링은 다시 하지 않아도 됩니다.
- **기기를 여러 대 연결한 경우**: `error: more than one device/emulator`가 나오면 모든 명령에 `-s`로 대상을 지정합니다.
  ```
  adb -s 192.168.xxx.xxx:xxxxx install -r ToggleFullChargeForGalaxy-v1.4.1.apk
  ```
  또는 `adb disconnect`로 기존 연결을 모두 끊은 뒤 한 대만 연결합니다.
- **1.2 이하에서 업데이트하는 경우**: 1.3부터 패키지 이름이 `app.togglefullcharge`로 바뀌어 기존 앱 위에 덮어 설치되지 않습니다. 기존 앱에서 제한을 복구한 상태(위젯이 80%로 표시)인지 확인한 뒤 기존 앱을 삭제하고, 새 버전을 설치해 권한을 다시 부여해 주세요. 위젯도 다시 추가해야 합니다.
- **1.2 릴리스 삭제**: 1.2 이하는 공개된 디버그 키로 서명되어 있어, 다른 사람이 같은 키로 서명한 APK를 만들면 기존 앱 위에 덮어 설치되고 adb로 부여한 권한까지 이어받을 수 있습니다. 이 보안 문제로 1.2 릴리스를 삭제했습니다. 1.2 이하가 설치되어 있다면 위 방법으로 기존 앱을 삭제하고 최신 버전으로 옮겨 주세요.
- **업데이트**: 새 버전도 `adb install -r`로 덮어 설치하면 권한이 그대로 유지됩니다. 앱을 삭제한 뒤 다시 설치했다면 권한 부여 명령을 다시 실행해야 합니다.
- **페어링이 되지 않는 경우**: 학교·회사 Wi-Fi처럼 기기 간 통신을 막아 둔 네트워크에서는 연결되지 않을 수 있습니다. 이때는 PC와 휴대폰을 같은 핫스팟에 연결하거나, USB 케이블로 연결한 뒤(개발자 옵션 → USB 디버깅) 3·4번을 건너뛰고 진행합니다.
- **PowerShell을 사용하는 경우**: 현재 폴더의 프로그램을 실행하려면 `adb` 대신 `.\adb`를 입력합니다.
- **macOS**: 명령은 같습니다. 터미널에서 platform-tools 폴더로 이동한 뒤 `./adb`로 실행하며, sudo는 필요하지 않습니다.

## 동작 원리

- One UI 배터리 보호 설정은 `Settings.Global`의 `protect_battery` 값으로 저장됩니다.
  - 0 꺼짐 / 1 최대(`battery_protection_threshold`%) / 3 기본 / 4 수면 시간 보호(One UI 9)
- 위젯을 누르면 현재 값을 저장하고 `protect_battery`를 0으로 바꾼 뒤, 포그라운드 서비스가 배터리 상태를 감시합니다.
- 다음 중 하나가 되면 저장해 둔 값으로 되돌립니다.
  - 잔량 100% 또는 완충 상태 감지
  - 설정한 시간 초과 (기본 3시간)
  - 충전기를 15초 이상 분리 (옵션)
  - 알림의 "지금 제한 복구" 버튼, 위젯이나 앱 버튼을 다시 누름
  - 재부팅

## 빌드

- Android Studio에서 이 폴더를 열고 Run ▶을 누르면 됩니다. APK 이름은 `ToggleFullChargeForGalaxy-v<버전>-debug.apk`로 생성됩니다.
- 버전은 `app/build.gradle.kts`의 `appVersionName`, `appVersionCode` 두 값만 바꾸면 됩니다.
- `build_apk_nogradle.sh`는 Gradle 없이 aapt2, javac, dx, uber-apk-signer로 빌드하는 스크립트입니다. `TOOLS` 환경변수에 도구가 있는 폴더를 지정해야 합니다.

## 알려진 한계

- "충전기를 15초 이상 분리하면 복구" 옵션은 분리 후 15초를 기다렸다가 복구합니다. 무선 충전 패드에서 잠깐 어긋났다가 15초 안에 다시 충전되면 복구하지 않지만, 15초보다 오래 어긋나 있으면 복구됩니다.
- 시간 초과 알람은 정확한 알람 권한 없이 동작하므로 몇 분 늦을 수 있습니다. 충전 중에는 배터리 상태가 바뀔 때마다 추가로 확인합니다.
- 제한을 해제한 동안 설정 앱에서 배터리 보호를 직접 바꾸면, 복구할 때 해제 직전 값으로 덮어씁니다.

## 라이선스 및 면책

이 프로젝트는 [MIT 라이선스](LICENSE)로 배포됩니다. 누구나 자유롭게 사용, 복사, 수정, 배포할 수 있으며, 이때 저작권 표시와 라이선스 문구를 함께 포함해야 합니다.

이 프로젝트는 삼성전자 및 Apple과 관련이 없는 비공식 개인 프로젝트입니다. Samsung, Galaxy, One UI는 삼성전자의 상표이고 Mac은 Apple Inc.의 상표이며, 이 저장소에서는 호환 기기와 기능을 설명하는 용도로만 사용합니다.

이 소프트웨어는 "있는 그대로" 제공되며, 어떠한 종류의 보증도 하지 않습니다. 이 앱의 사용으로 발생하는 배터리 수명 저하, 기기 이상 등 모든 문제에 대해 작성자는 책임지지 않습니다. 사용 여부는 본인의 판단으로 결정해 주시기 바랍니다. 법적 효력은 [LICENSE](LICENSE) 파일의 영문 원문을 기준으로 합니다.

## 기여

이 저장소는 풀 리퀘스트를 받지 않습니다. 버그 제보나 제안은 [Issues](../../issues)에 남겨 주세요.
