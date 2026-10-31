# ToggleFullChargeForGalaxy (이번만 완전 충전)

맥의 "지금 완전히 충전"을 갤럭시에서 흉내 내는 앱. 위젯을 누르면 One UI 배터리 보호(최대 80%)를 잠시 끄고, 완충 또는 설정 시간이 지나면 원래 모드로 되돌린다.

- 루트 불필요. `WRITE_SECURE_SETTINGS` 권한을 adb로 한 번 부여해야 함
- 테스트 기기: One UI 8, One UI 9 기기

## 설치
[Releases](../../releases)에서 `ToggleFullChargeForGalaxy-v<버전>.apk`를 받은 뒤, PC에서 adb로 설치한다.

```
adb install -r ToggleFullChargeForGalaxy-v1.2.apk
adb shell pm grant io.github.nunnoo58.fullcharge android.permission.WRITE_SECURE_SETTINGS
```

- 무선 디버깅: 휴대폰 설정 → 개발자 옵션 → 무선 디버깅 → `adb pair <IP:포트>` → `adb connect <IP:포트>`
- `-r`로 덮어 설치하면 권한이 유지된다. 앱을 삭제 후 재설치하면 권한 부여를 다시 해야 한다.

## 호환성 확인
```
adb shell getprop ro.build.version.oneui
adb shell settings get global protect_battery
```
둘째 줄이 `null`이면 이 기기에는 해당 키가 없어 동작하지 않는다.

## 동작 원리
- One UI 배터리 보호는 `Settings.Global`의 `protect_battery` 값으로 저장된다.
  - 0 꺼짐 / 1 최대(`battery_protection_threshold`%) / 3 기본 / 4 수면 시간 보호(One UI 9)
- 위젯 탭 → 현재 값을 저장 → `protect_battery=0` → 포그라운드 서비스가 배터리 감시
- 복구 조건: 100% 또는 `BATTERY_STATUS_FULL` / 설정 시간 초과(기본 3시간) / (옵션) 충전기 분리 / 알림의 "지금 제한 복구" / 위젯·앱 버튼 재탭 / 재부팅

## 빌드
- Android Studio: 이 폴더를 열고 Run ▶. APK 이름은 `ToggleFullChargeForGalaxy-v<버전>-debug.apk`로 나온다.
- 버전은 `app/build.gradle.kts`의 `appVersionName`, `appVersionCode`만 바꾸면 된다.
- `build_apk_nogradle.sh`: Gradle 없이 aapt2 + javac + dx + uber-apk-signer로 빌드하는 스크립트. `TOOLS` 환경변수에 도구 폴더를 지정해야 한다.

## 알려진 한계
- 무선 충전기를 쓸 때는 "충전기 분리 시에도 바로 복구" 옵션을 끌 것. 패드에서 잠깐 어긋나도 분리로 감지된다.
- 시간 초과 알람은 정확 알람 권한 없이 동작하므로 수 분 늦을 수 있다(충전 중에는 배터리 이벤트마다 추가 확인).
- 해제 중에 설정 앱에서 직접 배터리 보호를 바꾸면, 복구 시 해제 직전 값으로 덮어쓴다.
