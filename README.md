# ToggleFullChargeForGalaxy (이번만 완전 충전)

맥의 "지금 완전히 충전" 기능을 갤럭시에서 흉내 내는 앱입니다. 위젯을 누르면 One UI 배터리 보호(최대 80%)를 잠시 끄고, 완충되거나 설정한 시간이 지나면 원래 제한으로 되돌립니다.

> 이 프로젝트는 삼성전자와 관련이 없는 비공식 개인 프로젝트입니다. Samsung, Galaxy, One UI는 삼성전자의 상표이며, 이 저장소에서는 호환 기기를 설명하는 용도로만 사용합니다.

- 루트 권한은 필요하지 않습니다. 대신 `WRITE_SECURE_SETTINGS` 권한을 adb로 한 번 부여해야 합니다.
- 테스트한 기기: One UI 8, One UI 9 기기

## 설치

[Releases](../../releases)에서 `ToggleFullChargeForGalaxy-v<버전>.apk`를 내려받은 뒤, PC에서 adb로 설치합니다.

```
adb install -r ToggleFullChargeForGalaxy-v1.2.apk
adb shell pm grant io.github.nunnoo58.fullcharge android.permission.WRITE_SECURE_SETTINGS
```

- 무선 디버깅으로 연결하려면: 휴대폰 설정 → 개발자 옵션 → 무선 디버깅을 켠 뒤 `adb pair <IP:포트>`, `adb connect <IP:포트>` 순서로 연결합니다.
- `-r` 옵션으로 덮어 설치하면 권한이 유지됩니다. 앱을 삭제한 뒤 다시 설치했다면 권한 부여 명령을 다시 실행해야 합니다.

## 호환성 확인

```
adb shell getprop ro.build.version.oneui
adb shell settings get global protect_battery
```

두 번째 명령의 결과가 `null`이면 해당 기기에는 이 설정이 없어 앱이 동작하지 않습니다.

## 동작 원리

- One UI 배터리 보호 설정은 `Settings.Global`의 `protect_battery` 값으로 저장됩니다.
  - 0 꺼짐 / 1 최대(`battery_protection_threshold`%) / 3 기본 / 4 수면 시간 보호(One UI 9)
- 위젯을 누르면 현재 값을 저장하고 `protect_battery`를 0으로 바꾼 뒤, 포그라운드 서비스가 배터리 상태를 감시합니다.
- 다음 중 하나가 되면 저장해 둔 값으로 되돌립니다.
  - 잔량 100% 또는 완충 상태 감지
  - 설정한 시간 초과 (기본 3시간)
  - 충전기 분리 (옵션)
  - 알림의 "지금 제한 복구" 버튼, 위젯이나 앱 버튼을 다시 누름
  - 재부팅

## 빌드

- Android Studio에서 이 폴더를 열고 Run ▶을 누르면 됩니다. APK 이름은 `ToggleFullChargeForGalaxy-v<버전>-debug.apk`로 생성됩니다.
- 버전은 `app/build.gradle.kts`의 `appVersionName`, `appVersionCode` 두 값만 바꾸면 됩니다.
- `build_apk_nogradle.sh`는 Gradle 없이 aapt2, javac, dx, uber-apk-signer로 빌드하는 스크립트입니다. `TOOLS` 환경변수에 도구가 있는 폴더를 지정해야 합니다.

## 알려진 한계

- 무선 충전기를 사용한다면 "충전기 분리 시에도 바로 복구" 옵션은 꺼 두시기 바랍니다. 휴대폰이 패드에서 잠깐 어긋나기만 해도 분리로 감지됩니다.
- 시간 초과 알람은 정확한 알람 권한 없이 동작하므로 몇 분 늦을 수 있습니다. 충전 중에는 배터리 상태가 바뀔 때마다 추가로 확인합니다.
- 제한을 해제한 동안 설정 앱에서 배터리 보호를 직접 바꾸면, 복구할 때 해제 직전 값으로 덮어씁니다.

## 라이선스 및 면책

이 프로젝트는 [MIT 라이선스](LICENSE)로 배포됩니다. 누구나 자유롭게 사용, 복사, 수정, 배포할 수 있으며, 이때 저작권 표시와 라이선스 문구를 함께 포함해야 합니다.

이 소프트웨어는 "있는 그대로" 제공되며, 어떠한 종류의 보증도 하지 않습니다. 이 앱의 사용으로 발생하는 배터리 수명 저하, 기기 이상 등 모든 문제에 대해 작성자는 책임지지 않습니다. 사용 여부는 본인의 판단으로 결정해 주시기 바랍니다. 법적 효력은 [LICENSE](LICENSE) 파일의 영문 원문을 기준으로 합니다.
