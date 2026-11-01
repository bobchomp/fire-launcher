#!/usr/bin/env bash
# Checks this computer for what Fire Launcher needs:
#   - JDK 17 or newer (to run Gradle and build the APK)
#   - Android SDK     (to build; platform 37 and build-tools)
#   - adb             (to install on the Fire TV)
# Prints install hints for anything missing. Changes nothing.
set -uo pipefail

ok()   { printf '  \033[32mOK\033[0m    %s\n' "$1"; }
miss() { printf '  \033[31mMISSING\033[0m %s\n' "$1"; missing=1; }
missing=0

case "$(uname -s)" in
  Darwin) os=mac ;;
  Linux)  os=linux ;;
  MINGW*|MSYS*|CYGWIN*) os=windows ;;
  *) os=other ;;
esac

hint() {
  local mac="$1" linux="$2" windows="$3"
  case "$os" in
    mac) echo "        install: $mac" ;;
    linux) echo "        install: $linux" ;;
    windows) echo "        install: $windows" ;;
  esac
}

echo "JDK 17+"
java_bin=""
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then java_bin="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then java_bin="$(command -v java)"; fi
if [[ -n "$java_bin" ]]; then
  ver_line="$("$java_bin" -version 2>&1 | grep -m1 -E 'version "')"
  ver="$(sed -E 's/.*version "([0-9]+)(\.([0-9]+))?.*/\1 \3/' <<<"$ver_line")"
  major="${ver%% *}"; [[ "$major" == 1 ]] && major="${ver##* }" # "1.8" style
  if [[ "$major" =~ ^[0-9]+$ && "$major" -ge 17 ]]; then ok "Java $major ($java_bin)"
  else miss "Java $major found at $java_bin, but 17 or newer is required"; fi
else
  miss "no java on PATH and JAVA_HOME not set"
fi
if [[ $missing == 1 ]]; then
  hint "brew install --cask temurin@21" \
       "sudo apt install openjdk-21-jdk   (Fedora: sudo dnf install java-21-openjdk-devel)" \
       "winget install EclipseAdoptium.Temurin.21.JDK"
fi

echo "Android SDK"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk" && -f local.properties ]]; then
  sdk="$(sed -n 's/^sdk\.dir=//p' local.properties | sed 's/\\:/:/g; s/\\\\/\\/g')"
fi
if [[ -z "$sdk" ]]; then
  for guess in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "${LOCALAPPDATA:-}/Android/Sdk"; do
    [[ -d "$guess" ]] && sdk="$guess" && break
  done
fi
sdk_missing=0
if [[ -n "$sdk" && -d "$sdk" ]]; then
  ok "SDK at $sdk"
  [[ -n "${ANDROID_HOME:-}" || -f local.properties ]] || \
    echo "        note: set ANDROID_HOME=$sdk or put sdk.dir=$sdk in local.properties"
else
  miss "no Android SDK found (checked ANDROID_HOME, local.properties, default locations)"
  sdk_missing=1
fi
if [[ $sdk_missing == 1 ]]; then
  echo "        install: Android Studio (includes the SDK), or the command-line tools from"
  echo "                 https://developer.android.com/studio#command-line-tools-only and then:"
  echo "                 sdkmanager \"platform-tools\" \"platforms;android-37.0\" \"build-tools;37.0.0\""
fi

echo "adb (Android platform-tools)"
adb_bin=""
if command -v adb >/dev/null 2>&1; then adb_bin="$(command -v adb)"
elif [[ -n "$sdk" && -x "$sdk/platform-tools/adb" ]]; then adb_bin="$sdk/platform-tools/adb"; fi
if [[ -n "$adb_bin" ]]; then
  ok "$("$adb_bin" version | head -1) ($adb_bin)"
  command -v adb >/dev/null 2>&1 || echo "        note: add $(dirname "$adb_bin") to PATH"
else
  miss "adb not found"
  hint "brew install --cask android-platform-tools" \
       "sudo apt install adb   (Fedora: sudo dnf install android-tools)" \
       "winget install Google.PlatformTools"
fi

echo
if [[ $missing == 0 ]]; then
  echo "All set. Build with ./gradlew assembleRelease, then run scripts/deploy.sh <fire-tv-ip>."
else
  echo "Install the missing pieces above, open a new terminal, and run this again."
  exit 1
fi
