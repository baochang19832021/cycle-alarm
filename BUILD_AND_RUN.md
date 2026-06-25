# Android Studio Build And Run

## Open The Project

1. In Android Studio, choose `Open` and select this project root:
   `D:\360MoveData\Users\ALIENWARE\Documents\制作手机周期闹钟`.
2. When Android Studio asks for the Gradle JDK, select the embedded JDK:
   `E:\androidstudio\jbr`.
3. Confirm that Android SDK Platform 34 is installed in SDK Manager.
4. Wait for Gradle Sync to complete. The project supports Android 5.0 and above (`minSdk 21`).

## Run On A Phone

1. Enable Developer Options and USB Debugging on the phone.
2. Connect the phone by USB and accept the debugging authorization prompt.
3. Select the phone in the device list, then press Run.
4. For a locally built APK, use:
   `app\build\outputs\apk\debug\app-debug.apk`.

## First Test Checklist

- Enter the Every N Days screen and scroll the hour and minute wheels slowly and quickly.
- Confirm each number change has a light click sound and the wheel settles on the center line.
- Create an alarm with `N = 0`; it should be a one-time alarm today and become inactive after dismissal.
- Create alarms with `N = 1` and `N = 40`; verify the next trigger time and enable/disable/delete controls.
- Set an alarm a few minutes ahead, lock the phone, then verify ringing, vibration setting, dismiss and 5-minute snooze.
- Restart the phone and verify an active alarm is registered again.

## Build From Command Line

Run `build_test.bat` from the project root. It uses the embedded JDK from the installed Android Studio and the verified Gradle 8.5 cache on this computer.
