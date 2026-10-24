# DriveMount

rclone engine ভিত্তিক Android Cloud Storage Manager.

## কাঠামো
- `engine/rclone-master.zip` — rclone official source (অপরিবর্তিত)। GitHub Actions এটি unzip করে `.aar` বানায়।
- `app/` — Kotlin + Jetpack Compose অ্যাপ
- `.github/workflows/build.yml` — APK build workflow

## APK পাওয়ার ধাপ
1. ZIP extract করুন। ভেতরের **সব ফাইল ও ফোল্ডার** (`.github`, `app`, `engine`, `build.gradle.kts` ইত্যাদি) GitHub repo-র root-এ upload করুন (branch: `main`)। পুরোনো ফাইল replace হবে।
2. repo-র **Actions** ট্যাবে `Build DriveMount APK` চালু হবে (বা **Run workflow** চাপুন)।
3. শেষ হলে run-এর নিচে **Artifacts** থেকে `DriveMount-debug-apk` ডাউনলোড করুন।

প্রথম build-এ ২০–৪০ মিনিট লাগতে পারে (rclone কম্পাইল হয়)। পরের build-এ cache থেকে অনেক দ্রুত হবে।

## ধাপের অবস্থা
1-2. প্রজেক্ট কাঠামো + rclone engine ✓
3. Theme system ও Settings ✓
4. অ্যাকাউন্ট ব্যবস্থাপনা (Room) + rclone bridge ✓
5. Google Drive, Mega, pCloud যোগ করা (browser সাইন-ইন সহ) ✓
6. ফাইল ব্রাউজার (ফোল্ডার, নতুন ফোল্ডার, নাম বদল, মোছা, কপি/কাটা/পেস্ট, খোঁজা, সাজানো) ✓
7. ট্রান্সফার কিউ (ব্যাকগ্রাউন্ডে ডাউনলোড/আপলোড/কপি/সরানো, অগ্রগতি, বাতিল), ফাইল খোলা ✓
8. Files অ্যাপে অ্যাকাউন্ট দেখানো (mount), Sync (Copy/Mirror/Move, স্বয়ংক্রিয় সময়সূচি) ✓
8b. Terabox (ndus কুকি দিয়ে) — আমাদের নিজস্ব rclone backend: engine/terabox/terabox.go ✓
9. পরবর্তী: সেটিংস ও নিরাপত্তা (Wi-Fi সীমা, গতি সীমা, বায়োমেট্রিক লক, config encryption)
