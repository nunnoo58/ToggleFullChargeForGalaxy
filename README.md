# 이번만 완전 충전 (FullChargeOnce)

맥의 "지금 완전히 충전"을 갤럭시에서 흉내 내는 앱. 위젯을 누르면 One UI 배터리 보호(최대 80%)를 잠시 끄고, 완충 또는 설정 시간이 지나면 원래 모드로 되돌린다.

## 동작 원리
- One UI 배터리 보호는 `Settings.Global`의 `protect_battery` 값으로 저장됨
  - 0 꺼짐 / 1 최대(`battery_protection_threshold`%) / 3 기본 / 4 수면 시간 보호
- 위젯 탭 → 현재 값을 저장 → `protect_battery=0` → 포그라운드 서비스가 배터리 감시
- 복구 조건: 100% 또는 `BATTERY_STATUS_FULL` / 설정 시간 초과(기본 3시간) / (옵션) 충전기 분리 / 알림의 "지금 제한 복구" / 재부팅
- 일반 앱은 이 값을 쓸 수 없으므로 `WRITE_SECURE_SETTINGS`를 adb로 1회 부여해야 함(루트 불필요)

## 빌드
1. Android Studio로 이 폴더 열기 → Gradle 동기화(AGP 업그레이드 제안이 뜨면 수락해도 됨)
2. 휴대폰: 설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호 7회 탭 → 개발자 옵션 → USB 디버깅 켜기
3. USB 연결 후 Run ▶ 으로 설치

## 권한 부여 (설치 후 1회, 재설치하면 다시)
```
adb shell pm grant io.github.nunnoo58.fullcharge android.permission.WRITE_SECURE_SETTINGS
```
adb 경로: Windows `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`, macOS `~/Library/Android/sdk/platform-tools/adb`

## 호환성 확인
```
adb shell settings get global protect_battery
adb shell settings get global battery_protection_threshold
```
첫 줄이 `null`이면 이 기기에는 해당 키가 없어 동작하지 않음.

## 알려진 한계
- 충전기가 꽂힌 채 80%에서 멈춘 상태로 값을 바꿨을 때 충전이 즉시 재개되는지는 기기·One UI 버전별로 검증이 덜 됨. 재개되지 않으면 앱이 알림으로 케이블 재연결을 안내함.
- 시간 초과 알람은 정확 알람 권한 없이 동작하므로 수 분 늦을 수 있음(충전 중에는 배터리 이벤트마다 추가 확인).
- 해제 중에 설정 앱에서 직접 배터리 보호를 바꾸면, 복구 시 해제 직전 값으로 덮어씀.
