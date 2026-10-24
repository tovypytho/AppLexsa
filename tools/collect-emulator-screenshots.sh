#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/reports/screenshots
for page in 01-beranda 02-pengaturan 03-diagnostik; do
  adb exec-out run-as com.tovypytho.applexsa cat "cache/screenshots/$page.png" > "app/build/reports/screenshots/$page.png"
done
