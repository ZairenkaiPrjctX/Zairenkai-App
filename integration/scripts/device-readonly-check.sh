#!/usr/bin/env bash
# Read-only snapshot of a connected Android device, for the validation record
# (docs/testing/device-validation.md). Run it before installing anything, after installing
# (before the reboot), and after the reboot; keep each output.
#
# Usage: device-readonly-check.sh [--root] [-s <serial>] > before.txt
#   --root  also run read-only commands through su: su -v/-V, manager CLI version and module
#           list, the two Zairenkai module directories, getenforce, and the Core/FAS status.
#           The root manager asks for confirmation. Nothing is written by any command.
# Requires: adb. Commands are fixed; nothing from the device is used to build a command.
set -euo pipefail

ROOT=0
ADB=(adb)
while [ $# -gt 0 ]; do
  case "$1" in
    --root) ROOT=1 ;;
    -s) ADB+=(-s "$2"); shift ;;
    *) echo "unknown argument $1" >&2; exit 2 ;;
  esac
  shift
done

sh_() { echo "\$ $*"; "${ADB[@]}" shell "$@" 2>&1 || echo "(exit $?)"; echo; }
su_() { echo "# su -c '$1'"; "${ADB[@]}" shell su -c "$1" 2>&1 || echo "(exit $?)"; echo; }

echo "== Zairenkai read-only device check, $(date -u +%Y-%m-%dT%H:%M:%SZ)"
"${ADB[@]}" get-state >/dev/null
for p in ro.product.manufacturer ro.product.brand ro.product.model ro.product.device \
         ro.soc.manufacturer ro.soc.model ro.board.platform ro.build.version.release \
         ro.build.version.sdk ro.build.version.security_patch ro.product.cpu.abilist ro.build.type; do
  printf '%s=%s\n' "$p" "$("${ADB[@]}" shell getprop "$p" | tr -d '\r')"
done
echo
sh_ uname -a
sh_ getconf PAGE_SIZE
sh_ ls /sys/class/thermal
sh_ ls -d /sys/devices/system/cpu/cpufreq
sh_ pm list packages com.topjohnwu.magisk me.weishu.kernelsu com.rifsxd.ksunext me.bmax.apatch com.zairenkaiprjctx.app
sh_ dumpsys package com.zairenkaiprjctx.app
echo "\$ sha256sum of the installed APK"
for apk in $("${ADB[@]}" shell pm path com.zairenkaiprjctx.app 2>/dev/null | tr -d '\r' | sed -n 's/^package://p'); do
  case "$apk" in /data/app/*.apk) "${ADB[@]}" shell sha256sum "$apk" 2>&1 || echo "(not readable as shell)" ;; esac
done
echo
if [ "$ROOT" = 1 ]; then
  su_ 'id'
  su_ 'su -v'
  su_ 'su -V'
  su_ 'magisk -V'
  su_ '/data/adb/ksud module list'
  su_ '/data/adb/apd module list'
  for id in zairenkai_core zairenkai_fas; do
    for d in /data/adb/modules /data/adb/modules_update; do
      su_ "ls -la $d/$id"
      su_ "cat $d/$id/module.prop"
    done
  done
  su_ 'getenforce'
  su_ '/data/adb/modules/zairenkai_core/bin/zcored ctl --dir /data/adb/zairenkai-core health'
  su_ '/data/adb/modules/zairenkai_fas/bin/zfasd ctl --dir /data/adb/zairenkai-fas status'
  su_ 'cat /data/adb/zairenkai-core/failed'
  su_ 'tail -n 50 /data/adb/zairenkai-core/zcored.log'
fi
echo "== SELinux denials in the current log buffer (avc)"
"${ADB[@]}" logcat -d -b all 2>/dev/null | grep -i 'avc:.*denied' | grep -iE 'zairenkai|zcored|zfasd|zthermal|zart' || echo "(none matching)"
