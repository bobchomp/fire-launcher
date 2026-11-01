#!/usr/bin/env bash
# Installs Fire Launcher on a Fire TV over adb and sets up the Home-button redirect.
#
#   scripts/deploy.sh 192.168.1.50          # network adb (port 5555 by default)
#   scripts/deploy.sh                       # already-connected / USB device
#   scripts/deploy.sh 192.168.1.50 path/to/app.apk
#
# On the Fire TV first: Settings > My Fire TV > Developer options > ADB debugging ON.
# (If Developer options is hidden, open My Fire TV > About and click the device name 7 times.)
# The IP address is under Settings > My Fire TV > About > Network.
set -euo pipefail
cd "$(dirname "$0")/.."

PKG=com.bobchomp.firelauncher
SERVICE="$PKG/$PKG.HomeButtonService"
target="${1:-}"
apk="${2:-app/build/outputs/apk/release/app-release.apk}"

adb="$(command -v adb || true)"
if [[ -z "$adb" ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -n "$sdk" && -x "$sdk/platform-tools/adb" ]] && adb="$sdk/platform-tools/adb"
fi
[[ -n "$adb" ]] || { echo "adb not found; run scripts/check-env.sh" >&2; exit 1; }

if [[ ! -f "$apk" ]]; then
  echo "== Building release APK"
  ./gradlew assembleRelease
fi

if [[ -n "$target" ]]; then
  [[ "$target" == *:* ]] || target="$target:5555"
  echo "== Connecting to $target (accept the prompt on the TV the first time)"
  out="$("$adb" connect "$target")"; echo "$out"
  [[ "$out" == *"connected"* && "$out" != *"failed"* ]] || { echo "Couldn't connect." >&2; exit 1; }
  export ANDROID_SERIAL="$target"
fi

state="$("$adb" get-state 2>/dev/null || true)"
if [[ "$state" != "device" ]]; then
  echo "Device not ready (state: ${state:-none}). If the TV shows 'Allow USB debugging?', choose OK and rerun." >&2
  exit 1
fi

echo "== Installing $apk"
"$adb" install -r "$apk"

echo "== Granting log access (used to spot the Home button)"
if "$adb" shell pm grant "$PKG" android.permission.READ_LOGS; then
  if "$adb" shell dumpsys package "$PKG" | grep -q "android.permission.READ_LOGS: granted=true"; then
    echo "   granted"
  else
    echo "   WARNING: the grant didn't stick; the Home button redirect won't work." >&2
  fi
else
  echo "   WARNING: pm grant failed; the Home button redirect won't work." >&2
fi

# Also try the accessibility route. Fire OS 6.7.x silently wipes this setting, but
# other builds allow it, and it hides the stock home more cleanly where it works.
current="$("$adb" shell settings get secure enabled_accessibility_services | tr -d '\r')"
if [[ -z "$current" || "$current" == "null" ]]; then
  services="$SERVICE"
elif [[ ":$current:" == *":$SERVICE:"* ]]; then
  services="$current"
else
  services="$current:$SERVICE" # keep VoiceView or anything else already on
fi
"$adb" shell settings put secure enabled_accessibility_services "$services" || true
"$adb" shell settings put secure accessibility_enabled 1 || true

# Restart so the app picks up the new permission.
"$adb" shell am force-stop "$PKG"

echo "== Opening Fire Launcher"
"$adb" shell am start -n "$PKG/.MainActivity" >/dev/null
echo "Done. Press Home on the remote to check the redirect."
