# Fire Launcher

A lightweight Android TV launcher for Amazon Fire TV, written in Kotlin with
Jetpack Compose for TV.

Version 1:

- Grid of installed apps, using each app's TV banner (falls back to icon + name)
- D-pad navigation with a focus zoom and border
- Clock and date, updated once a minute
- Settings shortcut (you can't reach the stock home screen once the redirect is on)
- An accessibility service that catches the **Home** button and brings this
  launcher to the front, because Fire OS doesn't let you change the default home app

Min SDK 25 (Fire OS 6 / Android 7.1). The release APK is under 1 MB.

## 1. Check your computer

```bash
scripts/check-env.sh
```

It checks for **JDK 17+**, the **Android SDK**, and **adb**, and prints the install
command for your OS for anything missing. Quick reference:

| Need | macOS | Ubuntu/Debian | Windows |
| --- | --- | --- | --- |
| JDK 17+ | `brew install --cask temurin@21` | `sudo apt install openjdk-21-jdk` | `winget install EclipseAdoptium.Temurin.21.JDK` |
| adb | `brew install --cask android-platform-tools` | `sudo apt install adb` | `winget install Google.PlatformTools` |
| Android SDK | Android Studio, or the [command-line tools](https://developer.android.com/studio#command-line-tools-only) | same | same |

With only the command-line tools, install the SDK pieces this project uses:

```bash
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
```

Then point the build at the SDK with `ANDROID_HOME`, or a `local.properties` file
containing `sdk.dir=/path/to/Android/Sdk`.

## 2. Build

```bash
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Use the
release build on the stick: R8 makes it about 10x smaller and noticeably faster than
the debug build. It is signed with your local debug key, which is fine for
sideloading. Set up a real signing config before you distribute it.

## 3. Install on the Fire TV

On the Fire TV:

1. **Settings > My Fire TV > Developer options > ADB debugging: ON.**
   If Developer options is hidden, go to **My Fire TV > About** and click the
   device name 7 times.
2. Find the IP address under **Settings > My Fire TV > About > Network**.

Then, from this repo:

```bash
scripts/deploy.sh 192.168.1.50
```

This connects over network adb, installs the APK, turns on the Home-button service
(keeping any services you already have, such as VoiceView), and opens the launcher.
The first time, the TV asks you to allow debugging: choose **OK** and run the script
again if it stopped.

### Turning the Home-button service on or off by hand

If the redirect is off, the launcher shows an **Enable Home button** button. It opens
Accessibility settings, where newer Fire OS versions list
"Fire Launcher Home button". Fire OS 6 may not list third-party services; use adb
instead:

```bash
adb shell settings put secure enabled_accessibility_services com.bobchomp.firelauncher/com.bobchomp.firelauncher.HomeButtonService
```

```bash
adb shell settings put secure accessibility_enabled 1
```

That first command replaces the whole list. If you use other accessibility services,
run `deploy.sh` instead, since it appends.

To get the stock Amazon home back, turn the service off:

```bash
adb shell settings delete secure enabled_accessibility_services
```

Uninstalling the app also turns it off.

## How the Home redirect works

`HomeButtonService` (an `AccessibilityService`) does two things:

1. **Catches the Home key.** With key-event filtering it swallows the Home press and
   opens the launcher, so the stock home screen never shows.
2. **Fallback.** It watches window changes. If the stock home *activity* shows up
   anyway, it puts the launcher on top. That covers Fire OS builds that handle Home
   before accessibility sees it, and backing out of an app (Android returns to the
   *default* home, which is still Amazon's).

At boot, Amazon's home is usually up before the service connects. So when the service
connects within the first 3 minutes after boot, it opens the launcher too.

The stock home activity is found at runtime: every other app that handles the `HOME`
intent. Other screens in Amazon's launcher package are left alone. The service never
reads screen content (`canRetrieveWindowContent="false"`).

Pressing Home while the launcher is already showing jumps back to the first app.

### Known limitations

- **Long-pressing Home no longer opens the Fire TV quick menu**, because the service
  swallows the key. Use the Settings shortcut in the launcher instead.
- **Fire OS updates can switch accessibility services off.** Run `deploy.sh` again, or
  use the in-app button.
- **If the stock home still appears and stays up**, your Fire OS build may use a
  different home activity. Run `adb logcat -s HomeButtonService` and press Home: lines
  like `Ignoring window com.amazon.tv.launcher/...` show what it saw.
- When the fallback is what catches it (not the key filter), the stock home can flash
  for a moment before the launcher covers it.

## Project layout

```
app/src/main/java/com/bobchomp/firelauncher/
  MainActivity.kt        Hosts the UI, reloads the app list on install/uninstall, launches apps
  AppRepository.kt       Queries LEANBACK_LAUNCHER and LAUNCHER activities
  AppArtLoader.kt        Lazy banner/icon decoding at 320x180 with a capped LRU cache
  HomeButtonService.kt   The Home-button accessibility service
  ui/LauncherScreen.kt   Top bar + 5-column focusable grid
  ui/AppCard.kt          Banner card (tv-material Card)
  ui/Clock.kt            Clock/date
app/src/main/res/xml/home_button_service.xml   Accessibility service config
scripts/check-env.sh     Checks JDK, SDK and adb
scripts/deploy.sh        Installs on the Fire TV and enables the service
```

Kept light for older sticks: no Material 3, navigation, image-loading or DI
libraries. Banners decode lazily per visible card at native TV banner size. The clock
wakes once a minute. The activity handles config changes instead of being recreated.

Toolchain: Gradle 9.8.2 (wrapper), Android Gradle Plugin 9.5.0, Kotlin 2.4.21,
Compose BOM 2026.09.00, tv-material 1.1.0, compileSdk 37, targetSdk 36, minSdk 25.
