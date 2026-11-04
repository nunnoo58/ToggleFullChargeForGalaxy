# Changelog

[한국어](CHANGELOG.md) | **English**

The APK for each version is available on the [Releases](https://github.com/nunnoo58/ToggleFullChargeForGalaxy/releases) page.

## v2.1.2 (2026-11-04)

- Added the GitHub address to the About page (tap it to open in a browser)

## v2.1.1 (2026-11-04)

- Added the "Keep charging at full until the duration ends" option (when on, the limit stays lifted after reaching 100% until the set time)
- When the Maximum limit of Battery protection is 100%, the app no longer lifts it and shows "Lower the charge limit in Settings first"; the widget shows "Lower limit"
- When a widget tap does not lift the limit (limit at 100%, no permission, and so on), the widget is redrawn with the current state

## v2.1 (2026-11-04)

- The English widget now shows "NO / Permission" when the permission is missing (fixes "Permission needed" being cut off in the 1×1 widget)

## v2.0 (2026-11-04)

- Added English and Japanese. Choose 한국어, English, or 日本語 from the menu (≡) → Languages... (also available in the system's app language settings)
- Follows the phone's language by default, and uses English for languages other than Korean, English, and Japanese
- The app name is "이번만 완전 충전" in Korean and "Toggle Full Charge" in other languages
- Battery Protection mode names now match the official One UI names ("Sleep time protection")
- The license name on the Legal notices page is now written simply as "CC BY-ND 4.0"

## v1.5.1 (2026-11-04)

- Changed the license from MIT to CC BY-ND 4.0 (unmodified copies may be redistributed with credit; modified versions may not be distributed; versions 1.5 and earlier stay under MIT)
- Updated the Legal notices page in the app with the new license

## v1.5 (2026-11-03)

- The charge limit is now restored after a reboot even before the phone is unlocked (previously it was restored at the first unlock)
- Removed the system top bar, fixing the app name being shown twice
- Status bar icons are now dark in light mode
- The notification permission is requested only once (not asked again after being denied)
- The screen updates immediately when the Battery Protection setting changes (fixes the status staying "Maximum" right after starting a full charge)
- Removed internal values (`protect_battery`, `threshold`, the value to restore) from the status, and shortened the label to "최대 유지 시간(1\~24)" (Maximum duration (1–24))
- Security: closed the receiver so other apps cannot send the restore signal

## v1.4.3 (2026-11-03)

- Fixed an out-of-range maximum duration whose first digit is valid, such as 25, being saved as the first digit (2)
- Values outside 1–24 now revert to the saved value with a message

## v1.4.2 (2026-11-02)

- Added space between the "이번만 완전 충전" (Full charge this time) and "새로고침" (Refresh) buttons on the main screen

## v1.4.1 (2026-11-02)

- Added a back button to the About and Legal notices pages

## v1.4 (2026-11-02)

- Added a 15-second grace period to the "restore when the charger is disconnected" option (stays lifted when the phone briefly shifts on a wireless charging pad)

## v1.3 (2026-11-02)

- Changed the package name to `app.togglefullcharge` (does not install over 1.2 or earlier)
- Signed with a dedicated release key
- Added About and Legal notices pages to the menu (≡) at the top right of the main screen

## v1.2 and earlier (2026-10-31)

- First public release
- Signed with a publicly known debug key; the release was removed because of this security issue
