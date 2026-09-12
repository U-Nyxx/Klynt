#!/usr/bin/env bash
# KLYNT one-click bug report — no manual logcat needed.
# Usage: bash tools/bugreport.sh
# Output: klynt-bugreport-<date>.txt (attach it to the issue/chat).
set -u
OUT="klynt-bugreport-$(date +%Y%m%d-%H%M%S).txt"
{
  echo "=== device ==="
  adb shell getprop ro.product.model ro.product.brand ro.build.version.release ro.board.platform 2>&1
  echo "=== ram ==="
  adb shell cat /proc/meminfo 2>/dev/null | head -3
  echo "=== klynt manager ==="
  adb shell dumpsys package com.unyxx.act 2>/dev/null | grep -E "versionName|versionCode|lastUpdateTime" | head -5
  echo "=== targets ==="
  for p in org.telegram.messenger com.twitter.android; do
    adb shell dumpsys package "$p" 2>/dev/null | grep -m1 versionName
  done
  echo "=== klynt hook log (last 200) ==="
  adb logcat -d 2>/dev/null | grep -i "KLYNT" | tail -200
  echo "=== crashes (AndroidRuntime, last 3) ==="
  adb logcat -d 2>/dev/null | grep -A20 "AndroidRuntime.*com.unyxx.act" | tail -80
  echo "=== crash.log inside manager (if any) ==="
  adb shell "run-as com.unyxx.act cat files/crash.log 2>/dev/null || echo '(no crash.log or not debuggable)'"
} > "$OUT" 2>&1
echo "Wrote $OUT — attach this file, no other info needed."
