# ToggleFullChargeForGalaxy

[한국어](README.md) | **English**

> This app was built with Claude, an AI assistant by Anthropic. Coding, building, and documentation were done together with Claude.

An app that mimics the Mac's "Charge to Full Now" feature on Galaxy devices. Tap the widget to temporarily turn off One UI Battery Protection (Maximum, 80%), and the original limit is restored once the battery is full or a set time has passed.

- No root required. Instead, the `WRITE_SECURE_SETTINGS` permission must be granted once via adb.
- Tested on: Galaxy S23, S26 (One UI 9)
- Planned: Galaxy Tab S8+ (One UI 8)
- The app's interface is in Korean. The on-screen labels referred to below are given as "Korean (English meaning)".

## Installation

Install the app and grant the permission from a PC using adb. The steps below use the Windows Command Prompt (CMD). All IP addresses, ports, and pairing codes in the examples are placeholders; enter the values shown on your phone's screen.

### 1. Download the tools

1. Download [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) and extract it anywhere. Example: `C:\platform-tools`
2. Download `ToggleFullChargeForGalaxy-v<version>.apk` from [Releases](../../releases) and put it in the same folder.
3. Open a Command Prompt in that folder. Typing `cmd` in File Explorer's address bar and pressing Enter opens it right there.

### 2. Turn on wireless debugging on the phone

1. Go to Settings → About phone → Software information and tap **Build number** 7 times until you see "Developer mode has been turned on."
2. Go to Settings → Developer options and turn on **Wireless debugging**.
3. The PC and phone must be on the **same Wi-Fi network**.

### 3. Pair (once per device)

On the phone, go to Wireless debugging → **Pair device with pairing code**. A screen like this appears:

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

### 4. Connect

After closing the pairing dialog, the main Wireless debugging screen shows a separate **IP address & Port**. This port is different from the pairing port.

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

### 5. Check compatibility

```
adb shell getprop ro.build.version.oneui
90000

adb shell settings get global protect_battery
1
```

The first result is the One UI version (80000 is One UI 8, 90000 is One UI 9).

If the second result is `0`, `1`, `3`, or `4`, the app can be used. If it is `null`, the device does not have this setting and the app will not work.

### 6. Install and grant the permission

```
adb install -r ToggleFullChargeForGalaxy-v1.3.apk
Performing Streamed Install
Success

adb shell pm grant io.github.nunnoo58.togglefullcharge android.permission.WRITE_SECURE_SETTINGS
```

The permission command prints nothing when it succeeds. Open the app, check that it shows "권한: 부여됨" (Permission: granted), and then add the widget to your home screen.

### Notes

- **After a reboot or a Wi-Fi change**: Wireless debugging turns off. When you turn it back on, the connection port changes, so start again from step 4 using the newly shown address. You do not need to pair again.
- **With multiple devices connected**: If you see `error: more than one device/emulator`, specify the target with `-s` on every command.
  ```
  adb -s 192.168.xxx.xxx:xxxxx install -r ToggleFullChargeForGalaxy-v1.3.apk
  ```
  Alternatively, run `adb disconnect` to drop all existing connections and connect only one device.
- **Updating from 1.2 or earlier**: Starting with 1.3, the package name changed to `io.github.nunnoo58.togglefullcharge`, so it does not install over the old app. Make sure the limit is restored in the old app (the widget shows 80%), uninstall the old app, then install the new version and grant the permission again. You will also need to add the widget again.
- **Updating**: Installing a new version over the old one with `adb install -r` keeps the permission. If you uninstalled the app and installed it again, run the permission command again.
- **If pairing fails**: Networks that block device-to-device traffic, such as school or office Wi-Fi, may prevent connecting. In that case, connect the PC and phone to the same hotspot, or connect with a USB cable (Developer options → USB debugging) and skip steps 3 and 4.
- **Using PowerShell**: To run a program in the current folder, type `.\adb` instead of `adb`.
- **macOS**: The commands are the same. In Terminal, go to the platform-tools folder and run `./adb`. sudo is not required.

## How it works

- The One UI Battery Protection setting is stored as the `protect_battery` value in `Settings.Global`.
  - 0 Off / 1 Maximum (`battery_protection_threshold`%) / 3 Basic / 4 Sleep time protection (One UI 9)
- When you tap the widget, the app saves the current value, sets `protect_battery` to 0, and a foreground service monitors the battery.
- The saved value is restored when any of the following happens:
  - Battery reaches 100% or the full-charge state is detected
  - The set time runs out (3 hours by default)
  - The charger is disconnected (optional)
  - The "지금 제한 복구" (Restore limit now) button in the notification is tapped, or the widget or app button is tapped again
  - The device reboots

## Building

- Open this folder in Android Studio and press Run ▶. The APK is named `ToggleFullChargeForGalaxy-v<version>-debug.apk`.
- To change the version, edit only `appVersionName` and `appVersionCode` in `app/build.gradle.kts`.
- `build_apk_nogradle.sh` builds the app without Gradle, using aapt2, javac, dx, and uber-apk-signer. Set the `TOOLS` environment variable to the folder containing these tools.

## Known limitations

- If you use a wireless charger, keep the "충전기 분리 시에도 바로 복구" (Restore immediately when charger is disconnected) option off. Even a brief shift on the pad is detected as a disconnection.
- The timeout alarm runs without the exact alarm permission, so it may be a few minutes late. While charging, the app also checks on every battery status change.
- If you change Battery Protection directly in Settings while the limit is lifted, the value from just before lifting is written back when restoring.

## License and disclaimer

This project is distributed under the [MIT License](LICENSE). Anyone may freely use, copy, modify, and distribute it, provided the copyright notice and license text are included.

This is an unofficial personal project and is not affiliated with Samsung Electronics or Apple. Samsung, Galaxy, and One UI are trademarks of Samsung Electronics, and Mac is a trademark of Apple Inc.; they are used in this repository only to describe compatible devices and features.

This software is provided "as is," without warranty of any kind. The author is not responsible for any problems arising from the use of this app, including reduced battery lifespan or device malfunction. Please use it at your own discretion. The English text of the [LICENSE](LICENSE) file is legally authoritative.

## Contributing

This repository does not accept pull requests. Please report bugs or send suggestions via [Issues](../../issues).
