#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
build_root="${SHESHIELD_BUILD_ROOT:-/tmp/sheshield-android-build}"
: "${SHESHIELD_SDK:=${ANDROID_HOME:-/tmp/sheshield-tools/sdk}}"
export JAVA_HOME="${JAVA_HOME:-/tmp/sheshield-tools/jdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/sheshield-gradle}"
python3 - "$project_root" "$build_root" "$SHESHIELD_SDK" <<'PY'
import shutil,sys
from pathlib import Path
root,dest,sdk=map(Path,sys.argv[1:])
shutil.copytree(root/'android',dest,dirs_exist_ok=True,ignore=shutil.ignore_patterns('build','.gradle','.idea','local.properties','.kotlin'))
(dest/'local.properties').write_text('sdk.dir='+str(sdk)+'\nBACKEND_BASE_URL=http://10.0.2.2:8787\n')
PY
cd "$build_root"
./gradlew "${@:-:app:assembleDebug}" --console=plain
if [ -f app/build/outputs/apk/debug/app-debug.apk ]; then
    mkdir -p "$project_root/artifacts"
    cp app/build/outputs/apk/debug/app-debug.apk "$project_root/artifacts/SheShield-debug.apk"
fi
if [ -d app/schemas ]; then cp -r app/schemas "$project_root/android/app/"; fi

if [ -f app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk ]; then cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk "$project_root/artifacts/SheShield-test.apk"; fi
