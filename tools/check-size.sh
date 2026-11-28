#!/usr/bin/env bash
# সম্পূর্ণ সোর্স কোডের zip যেন ৩০MB-এর বেশি না হয়। দেখুন: SIZE_BUDGET.md
# ব্যবহার: bash tools/check-size.sh   (প্রজেক্টের root থেকে)
set -euo pipefail
cd "$(dirname "$0")/.."
LIMIT=$((30 * 1024 * 1024))
WARN=$((24 * 1024 * 1024))
TMP="$(mktemp -d)/src.zip"
zip -q -9 -r "$TMP" . -x '.git/*' 'build/*' '*/build/*' '.gradle/*' 'app/libs/*' 'engine/rclone-master/*' '*.aar' '*.apk' '*.iml' '.idea/*'
SIZE=$(stat -c %s "$TMP")
echo "Source zip: $(( SIZE / 1024 )) KB (limit $(( LIMIT / 1024 )) KB)"
echo "Biggest files:"; du -ah --exclude=.git --exclude=build --exclude=.gradle --exclude=libs . | sort -rh | head -8
if [ "$SIZE" -gt "$LIMIT" ]; then echo "ERROR: over 30MB budget"; exit 1; fi
if [ "$SIZE" -gt "$WARN" ]; then echo "WARNING: above 24MB, trim before adding more"; fi
