# ToggleFullChargeForGalaxy

[한국어](README.md) | **English**

> This app was built with Claude, an AI assistant by Anthropic. Coding, building, and documentation were done together with Claude.

An app that mimics the Mac's "Charge to Full Now" feature on Galaxy devices. Tap the widget to temporarily turn off One UI Battery Protection (Maximum, 80%), and the original limit is restored once the battery is full or a set time has passed.

- No root required. Instead, the `WRITE_SECURE_SETTINGS` permission must be granted once via adb.
- Tested on: Galaxy S23, S26 (One UI 9), Galaxy Tab S8+ (One UI 8)
- The app's interface is in Korean. The on-screen labels referred to below are given as "Korean (English meaning)".

## Latest version: v1.5.1 (2026-11-04)

- Changed the license from MIT to CC BY-ND 4.0 (unmodified copies may be redistributed with credit; modified versions may not be distributed; versions 1.5 and earlier stay under MIT)
- Updated the Legal notices page in the app with the new license

See [CHANGELOG.en.md](CHANGELOG.en.md) for earlier versions, and get the APK from the [latest release](https://github.com/nunnoo58/ToggleFullChargeForGalaxy/releases/latest).

## Installation

Install the app and grant the permission from a PC using adb. The steps below use the Windows Command Prompt (CMD). All IP addresses, ports, and pairing codes in the examples are placeholders; enter the values shown on your phone's screen.

### 1. Download the tools

1. Download [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) and extract it anywhere. Example: `C:\platform-tools`
2. Download `ToggleFullChargeForGalaxy-v<version>.apk` from [Releases](../../releases) and put it in the same folder.
3. Open a Command Prompt in that folder. Typing `cmd` in File Explorer's address bar and pressing Enter opens it right there.

### 2. Connect the phone

First, turn on Developer options. Go to Settings → About phone → Software information and tap **Build number** 7 times until you see "Developer mode has been turned on."

Then connect using either a cable (2-1) or Wi-Fi (2-2). If you have a USB cable, the wired method is simpler.

#### 2-1. Connect with a USB cable

1. Go to Settings → Developer options and turn on **USB debugging**.
2. Connect the PC and phone with a USB cable. Charge-only cables do not carry data and will not work.
3. When the phone asks "Allow USB debugging?", check **Always allow from this computer** and tap Allow.
4. Check that the device appears in the list.

```
adb devices
List of devices attached
R3XXXXXXXXX      device
```

If it shows `unauthorized`, check the prompt on the phone. If the list is empty, check the cable and the USB debugging setting. Once connected, go to step 3.

#### 2-2. Connect over Wi-Fi

Go to Settings → Developer options and turn on **Wireless debugging**. The PC and phone must be on the **same Wi-Fi network**.

**Pair (once per device)**: On the phone, go to Wireless debugging → **Pair device with pairing code**. A screen like this appears:

```
Wi-Fi pairing code:  123456
IP address & Port:   192.168.xxx.xxx:xxxxx
```

On the PC, pair using the IP address and port shown, then enter the 6-digit code.

```
adb pair 192.168.xxx.xxx:xxxxx
Enter pairing code: 123456
Successfully paired to 192.168.xxx.xxx:xxxxx
```

**Connect**: After closing the pairing dialog, the main Wireless debugging screen shows a separate **IP address & Port**. This port is different from the pairing port.

```
IP address & Port:   192.168.xxx.xxx:xxxxx
```

Connect to this address and check that the device appears in the list.

```
adb connect 192.168.xxx.xxx:xxxxx
connected to 192.168.xxx.xxx:xxxxx

adb devices
List of devices attached
192.168.xxx.xxx:xxxxx      device
```

### 3. Check compatibility

```
adb shell getprop ro.build.version.oneui
90000

adb shell settings get global protect_battery
1
```

The first result is the One UI version (80000 is One UI 8, 90000 is One UI 9).

If the second result is `0`, `1`, `3`, or `4`, the app can be used. If it is `null`, the device does not have this setting and the app will not work.

### 4. Install and grant the permission

```
adb install -r ToggleFullChargeForGalaxy-v1.5.1.apk
Performing Streamed Install
Success

adb shell pm grant app.togglefullcharge android.permission.WRITE_SECURE_SETTINGS
```

The permission command prints nothing when it succeeds. Open the app, check that it shows "권한: 부여됨" (Permission: granted), and then add the widget to your home screen.

### Notes

- **After a reboot or a Wi-Fi change while connected over Wi-Fi**: Wireless debugging turns off. When you turn it back on, the connection port changes, so start again from the connect step in 2-2 using the newly shown address. You do not need to pair again.
- **With multiple devices connected**: If you see `error: more than one device/emulator`, specify the target with `-s` on every command.
  ```
  adb -s 192.168.xxx.xxx:xxxxx install -r ToggleFullChargeForGalaxy-v1.5.1.apk
  ```
  Alternatively, run `adb disconnect` to drop all existing connections and connect only one device.
- **Updating from 1.2 or earlier**: Starting with 1.3, the package name changed to `app.togglefullcharge`, so it does not install over the old app. Make sure the limit is restored in the old app (the widget shows 80%), uninstall the old app, then install the new version and grant the permission again. You will also need to add the widget again.
- **1.2 release removed**: Versions 1.2 and earlier were signed with a publicly known debug key, so anyone could build an APK signed with the same key that installs over the old app and inherits the permission granted via adb. The 1.2 release was removed because of this security issue. If you have 1.2 or earlier installed, uninstall it as described above and move to the latest version.
- **Updating**: Installing a new version over the old one with `adb install -r` keeps the permission. If you uninstalled the app and installed it again, run the permission command again.
- **If wireless pairing fails**: Networks that block device-to-device traffic, such as school or office Wi-Fi, may prevent connecting. In that case, connect the PC and phone to the same hotspot, or use the wired connection in 2-1.
- **Using PowerShell**: To run a program in the current folder, type `.\adb` instead of `adb`.
- **macOS**: The commands are the same. In Terminal, go to the platform-tools folder and run `./adb`.

## How it works

- The One UI Battery Protection setting is stored as the `protect_battery` value in `Settings.Global`.
  - 0 Off / 1 Maximum (`battery_protection_threshold`%) / 3 Basic / 4 Sleep time protection (One UI 9)
- When you tap the widget, the app saves the current value, sets `protect_battery` to 0, and a foreground service monitors the battery.
- The saved value is restored when any of the following happens:
  - Battery reaches 100% or the full-charge state is detected
  - The set time runs out (3 hours by default)
  - The charger stays disconnected for 15 seconds or more (optional)
  - The "지금 제한 복구" (Restore limit now) button in the notification is tapped, or the widget or app button is tapped again
  - The device reboots

## Building

- Open this folder in Android Studio and press Run ▶. The APK is named `ToggleFullChargeForGalaxy-v<version>-debug.apk`.
- To change the version, edit only `appVersionName` and `appVersionCode` in `app/build.gradle.kts`.
- `build_apk_nogradle.sh` builds the app without Gradle, using aapt2, javac, dx, and uber-apk-signer. Set the `TOOLS` environment variable to the folder containing these tools.

## Known limitations

- The "충전기를 15초 이상 분리하면 복구" (Restore when the charger is disconnected for 15 seconds or more) option waits 15 seconds after a disconnection before restoring. If the phone briefly shifts on a wireless charging pad and resumes charging within 15 seconds, the limit is not restored; if it stays off the pad longer, it is.
- The timeout alarm runs without the exact alarm permission, so it may be a few minutes late. While charging, the app also checks on every battery status change.
- If you change Battery Protection directly in Settings while the limit is lifted, the value from just before lifting is written back when restoring.
- In v1.4.2 and earlier, entering an out-of-range maximum duration whose first digit is valid, such as 25, saves only the first digit (2). The screen shows 25 but 2 hours is applied, so enter a value from 1 to 24 only.

## Testing

Results checked on a Galaxy S26 (One UI 9, v1.4.1–1.4.3), a Galaxy S23 (One UI 9, v1.4.3–1.5), and a Galaxy Tab S8+ (One UI 8, v1.4.2) on 2026-11-02–03. Charger disconnection, 100% battery, and stalled charging were checked by simulating battery states with `adb shell dumpsys battery`.

### Done

- [x] Permission is kept after updating over the old version with adb (1.4 → 1.4.1)
- [x] Opening the About and Legal notices pages from the menu (≡), and the back button
- [x] Lifting the limit (`protect_battery` 0) and restoring it with the widget and the app button
- [x] Restores when 100% is detected
- [x] Stays lifted when the charger is reconnected within 15 seconds
- [x] Restores when the charger stays disconnected for 15 seconds or more (including with the screen off)
- [x] Stays lifted on disconnection when the unplug option is off
- [x] When charging stalls for more than 90 seconds while connected, the notification shows a hint to reconnect the cable; the hint disappears when charging resumes
- [x] Reboot while lifted: restores during boot before the phone is unlocked (v1.5, S23; v1.4.3 and earlier restore at the first unlock)
- [x] Force-stopping the app while lifted: restores when the app is opened again (stays lifted until then)
- [x] Stays lifted when the phone briefly shifts on a wireless charging pad
- [x] Restores when the set time runs out
- [x] Restores with the "지금 제한 복구" (Restore limit now) button in the notification
- [x] Compatibility check, installation, and permission grant on Galaxy Tab S8+ (One UI 8)
- [x] App messages without the permission (status line, adb command box, "권한 없음" (No permission) toast)
- [x] Works with the notification permission denied
- [x] Entering 0 or an empty duration is ignored and the previous value is kept
- [x] Restores the original mode after lifting in the "Basic" and "Sleep time protection" modes
- [x] Tapping while Battery Protection is off shows "배터리 보호가 이미 꺼져 있음" (Battery Protection is already off)
- [x] Dark and light mode screens (main, About, Legal notices)
- [x] An out-of-range duration such as 25 reverts to the saved value with a message, and starting a full charge right after typing it uses the saved value (v1.4.3, S26 and S23)
- [x] On Galaxy S23 (One UI 9): removing the old app (1.1), installing v1.4.3, lifting and restoring, 100% detection, and the 15-second grace period
- [x] Without the permission, the widget shows "!" and "권한 필요" (Permission needed), and tapping it opens the app (S23)
- [x] Updating (1.4.3 → 1.5) keeps the duration, the unplug option, and an ongoing full charge (S23)
- [x] The notification permission is requested only once and not asked again after denial, relaunch, or a theme change (v1.5, S23)
- [x] The screen updates immediately right after starting a full charge and when Battery Protection is changed in Settings (v1.5, S23)
- [x] The app name is shown once, and status bar icons are dark in light mode (v1.5, S23)

## License and disclaimer

Starting with v1.5.1, this project is distributed under the [CC BY-ND 4.0](LICENSE). You may copy and redistribute it unmodified, including for commercial purposes, as long as you give credit (the author NUNNOO58 and this repository). You may not distribute modified versions.

Versions 1.5 and earlier were released under the MIT License, and the MIT terms continue to apply to those versions.

This is an unofficial personal project and is not affiliated with Samsung Electronics or Apple. Samsung, Galaxy, and One UI are trademarks of Samsung Electronics, and Mac is a trademark of Apple Inc.; they are used in this repository only to describe compatible devices and features.

This software is provided "as is," without warranty of any kind. The author is not responsible for any problems arising from the use of this app, including reduced battery lifespan or device malfunction. Please use it at your own discretion. The English text of the [LICENSE](LICENSE) file is legally authoritative.

## Contributing

This repository does not accept pull requests. Please report bugs or send suggestions via [Issues](../../issues).
