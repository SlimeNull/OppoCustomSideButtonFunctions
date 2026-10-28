# OnePlus Side Button Functions

[简体中文](README.md) | **English**

Configure actions for the side shortcut key on OnePlus, OPPO, and similar devices via Xposed/LSPosed. The app provides a Compose settings UI, while key interception and action execution mainly run inside the Android system framework process. The package name is `com.slimenull.customsidebuttonfunctions`.

<div align="center">
  <img src="images/preview-en.webp" alt="preview image" >
</div>

## Installation and Activation

1. Install the APK, enable the module in LSPosed/Vector, and add **Android System Framework (`android`)** to its scope.
2. Reboot the device so the system framework loads the module. Restarting only SystemUI does not necessarily make the key hooks in the framework process take effect.
3. Open "Side Button Functions", keep "Enable custom side button functions" turned on, choose an operation mode, and configure the actions. Settings are saved automatically, so later changes don't require manually tapping save.

When using "Xiaobu Shortcuts", you can also add `com.coloros.shortcuts` to the scope: when you open the ColorOS Shortcuts editing page, the module will try to copy the shortcut ID to the clipboard. This scope is not required for the side button itself to work, and it depends on how the corresponding system app is implemented.

## Operation Modes

There are three operation modes: "Off", "Simple", and "Morse Code". "Off" mode does not handle side button events, and the system continues to perform the original side button behavior. "Simple" and "Morse Code" are mutually exclusive; switching modes does not delete the configuration saved for the other mode.

## Volume Keys Controlling the Input Method Cursor

On the bottom "Other" page, under "Input Method Cursor", you can choose Off, Volume Up moves left, or Volume Up moves right. This feature is still governed by the master switch "Enable custom side button functions" on the home page: when the master switch is off, volume keys are not taken over. When enabled, if the screen is on, the device is unlocked, no call is in progress, the input method window is visible, and focus is not currently on the notification shade or control center, the volume up/down keys will send left/right arrow keys respectively. After hiding the input method or pulling down the notification shade, normal volume adjustment resumes automatically. Combinations of volume keys with the power key, or both volume keys together, are preserved as system key combinations and will not be converted.

Volume cursor mode can also be configured with a long-press action: do nothing, move continuously, or move to start/end. The long-press detection delay defaults to 500ms and the continuous movement interval defaults to 32ms (about 31 times per second); both can be adjusted on the page. When "move to start/end" is selected, the left direction moves to the beginning and the right direction moves to the end.

This feature intercepts system volume events in `system_server` and injects arrow keys based on the input method window state. For ROMs where volume events are passed directly to the input method service, an `InputMethodService` fallback hook is also provided. Changes to settings are hot-reloaded via shared preferences and don't require restarting the app; after changing the LSPosed scope or enabling the module for the first time, the system framework still needs to be restarted.

### Simple Operation

Configure actions separately for single click, double click, and long press. When the double-click action is set to "Do nothing", a single click triggers immediately after release; once a double-click is configured, a single click waits for a second press, with a default wait window of 300ms. A long press triggers when the button is held past the threshold, which defaults to 300ms; no single click is triggered again after release.

### Morse Code Operation

In "Morse Code Settings", add command sequences made up of `0` and `1`, and assign an action to each sequence. `0` is a short press and `1` is a long press. For example, `000` means three short presses in a row, and `01` means one short press followed by one long press.

- A press shorter than the "Long press duration" is counted as `0`; a press reaching that duration is counted as `1`. The default threshold is 300ms, adjustable from 100 to 800ms.
- If no further input occurs for longer than the "Command detection wait time" after release, the current sequence ends and an action match is attempted. The default wait time is 300ms, adjustable from 100 to 800ms.
- "Execute action as soon as possible" is on by default. If the current sequence exactly matches a command and no longer command begins with it, the action executes immediately: when the last digit is `0`, the check happens upon release; when the last digit is `1`, it happens when the long-press threshold is reached, without waiting for release. When turned off, the system waits for the input window to end even if a unique match has already been found.
- Input that has not yet matched is not cleared early; further key presses still count toward the same sequence. After the wait window ends, the entire invalid sequence is discarded, so that an erroneous input isn't mistaken for a different command.

"Vibrate on press" is off by default, and "Vibrate when long-press time is reached" is on by default. Both cues use Android's predefined short haptic effect. These key-press cues are independent of the vibration settings for after an action executes.

## Actions and Feedback

Both modes can choose from: ring/vibrate/silent cycle, Do Not Disturb, camera, flashlight, start/stop recording, screenshot, WeChat and Alipay shortcuts, ColorOS quick functions, Xiaobu Shortcuts, custom Activity, URL Scheme, and Shell commands. Morse code commands do not offer "Do nothing" as an action.

"Vibration and Prompts" on the home page configures feedback after an action is triggered: "Vibrate on trigger" is on by default and uses Android's predefined short haptic effect; the regular Toast is off by default, and its content can be edited. "Unknown Morse sequence prompt" is independent of the regular Toast and is off by default. When enabled, an invalid sequence ending will show custom text (default "Unknown operation sequence") and trigger two short haptic pulses with a 100ms interval between them.

In Simple mode, the double-click wait time and the long-press wait time both default to 300ms, with a range of 100 to 800ms for each.

"Advanced Settings" lets you configure the key code, the input device path, and whether to wake the device when the screen is off. The default key code is `735`, the input path is `/dev/input/event0`, and wake-on-screen-off is off by default.

The second page of the bottom navigation is "About", which retains the developer information and provides a GNU Lesser General Public License v3.0 license page and a link to the project repository: `https://github.com/SlimeNull/OppoCustomSideButtonFunctions`.

## Compatibility and Permissions

- The module first hooks Oplus's `StrategyActionButtonKeyLaunchApp`; if unavailable, it tries Android's `PhoneWindowManager`, and finally falls back to reading the Linux input device. Key implementations vary across devices and ROMs, so compatibility is not guaranteed.
- The volume key cursor feature requires adding **Android System Framework (`android`)** to the LSPosed/Vector scope; the foreground app and the specific input method usually do not need to be added to the scope. It relies on ColorOS/Android private input method and input event interfaces, so different system versions may require adaptation.
- The Do Not Disturb action requires the system to allow modifying the Do Not Disturb state; if a permission prompt appears, check the system's Do Not Disturb access settings.
- Screenshots prefer the system interface; on failure, they try a Shell command and a SystemUI compatibility broadcast. The method that actually works depends on the ROM.
- Custom Shell commands are first attempted in the app process via Shizuku; if Shizuku is unavailable, unauthorized, times out, or the command fails, it falls back to the existing `Activity + su` approach. On first launch, if Shizuku is detected, the app will request authorization. Neither method forwards the command to the launcher.
- When "Prefer direct su execution" in Advanced Settings is enabled, `su` is tried first, which requires the app to have root access; if `su` fails, Shizuku is tried next.
- App launching, certain quick functions, and Toast behavior may be restricted by the ROM. If falling back to raw input device reading, changing the input device path may require a device reboot before the device can be reopened.

## Building

Requires the Android SDK and the project's Gradle Wrapper. On Windows, run:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Output is located in `app/build/outputs/apk/debug/` or `app/build/outputs/apk/release/`. Release builds use the signing information configured in `keystore/keystore.properties`.
