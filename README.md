# Fire Launcher

A lightweight Android TV launcher for Amazon Fire TV, written in Kotlin with
Jetpack Compose for TV.

Version 1:

- Grid of installed apps, using each app's TV banner (falls back to icon + name)
- D-pad navigation with a focus zoom and border
- Clock and date, updated once a minute
- Settings shortcut (you can't reach the stock home screen once the redirect is on)
- A **Home** button redirect that brings this launcher to the front, because Fire OS
  doesn't let you change the default home app

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

The APK is written to `app/build/outputs/apk/release/fire-launcher-<version>-release.apk`. Use the
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

This connects over network adb, installs the APK, grants the permission the Home
redirect needs, and opens the launcher. The first time, the TV asks you to allow
debugging: choose **OK** and run the script again if it stopped.

On Windows, run it from **Git Bash** (comes with Git for Windows):
`bash scripts/deploy.sh 192.168.1.50 ~/Downloads/fire-launcher-1.1-release.apk`.

### Setting up the Home redirect by hand

`deploy.sh` does this for you. The manual equivalent:

```bash
adb shell pm grant com.bobchomp.firelauncher android.permission.READ_LOGS
```

```bash
adb shell am force-stop com.bobchomp.firelauncher
```

Then open Fire Launcher once. If the redirect is off, the launcher says so under the
clock.

To get the stock Amazon home back, revoke it (or uninstall the app):

```bash
adb shell pm revoke com.bobchomp.firelauncher android.permission.READ_LOGS
```

## How the Home redirect works

Fire OS doesn't let apps replace the home screen, so the launcher watches for
Amazon's home screen opening and covers it.

**`HomeWatcherService` (used on Fire OS 6.7.x).** Android logs every activity start
and resume. With `READ_LOGS`, which only adb can grant, a foreground service follows
those log lines. When the stock home activity is started (Home pressed) or resumed
(backing out of an app, boot), it brings the launcher to the front. The service
starts with the launcher and at boot.

**`HomeButtonService` (accessibility, where allowed).** On Fire OS builds that allow
third-party accessibility services, it swallows the Home key itself, so the stock
home never shows. Fire OS 6.7.1.1 silently wipes the setting that enables it, so
there the log watcher does the job. `deploy.sh` tries to enable both.

The stock home activity is found at runtime: every other app that handles the `HOME`
intent. Other screens in Amazon's launcher package are left alone.

Pressing Home while the launcher is already showing jumps back to the first app.

### Known limitations

- **Amazon's home flashes briefly** before the launcher covers it: the log line only
  appears once it is already opening.
- **Needs re-running `deploy.sh` after a factory reset**, because the `READ_LOGS`
  grant goes with it. App updates keep it.
- Where the accessibility route is active, long-pressing Home no longer opens the
  Fire TV quick menu. Use the Settings shortcut in the launcher instead.
- **If the stock home still appears and stays up**, run
  `adb logcat -s HomeWatcher` and press Home. It logs which home screen it is watching
  for and each time it acts.

## Project layout

```
app/src/main/java/com/bobchomp/firelauncher/
  MainActivity.kt        Hosts the UI, reloads the app list on install/uninstall, launches apps
  AppRepository.kt       Queries LEANBACK_LAUNCHER and LAUNCHER activities
  AppArtLoader.kt        Lazy banner/icon decoding at 320x180 with a capped LRU cache
  HomeWatcherService.kt  Home redirect via the system log (Fire OS 6.7.x)
  HomeButtonService.kt   Home redirect via accessibility (where Fire OS allows it)
  HomeRedirect.kt        Finds the stock home and brings the launcher forward
  BootReceiver.kt        Starts the watcher at boot
  ui/LauncherScreen.kt   Top bar + 5-column focusable grid
  ui/AppCard.kt          Banner card (tv-material Card)
  ui/Clock.kt            Clock/date
app/src/main/res/xml/home_button_service.xml   Accessibility service config
scripts/check-env.sh     Checks JDK, SDK and adb
scripts/deploy.sh        Installs on the Fire TV and sets up the Home redirect
```

Kept light for older sticks: no Material 3, navigation, image-loading or DI
libraries. Banners decode lazily per visible card at native TV banner size. The clock
wakes once a minute. The activity handles config changes instead of being recreated.

Toolchain: Gradle 9.8.2 (wrapper), Android Gradle Plugin 9.5.0, Kotlin 2.4.21,
Compose BOM 2026.09.00, tv-material 1.1.0, compileSdk 37, targetSdk 36, minSdk 25.
