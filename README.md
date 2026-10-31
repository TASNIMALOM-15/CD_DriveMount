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
9a. এনক্রিপ্টেড ভল্ট (rclone crypt), আপলোডের আগে এনক্রিপশনের প্রশ্ন, সংরক্ষিত লগইন এনক্রিপশন, বায়োমেট্রিক অ্যাপ লক ✓
9a++. ফোনের স্টোরেজে (Local) এনক্রিপ্টেড ভল্ট তৈরি/খোলা ✓
9a++++++. নতুন ভিডিও প্লেয়ার: প্লেলিস্ট, সাবটাইটেল/অডিও ট্র্যাক, গতি, জেসচার, PiP, শেষ অবস্থান মনে রাখা ✓
9a+++++. Backup এখন ৩ ফ্যাক্টর: দুই পাসওয়ার্ড + Backup Key (ক্রিপ্টোতে), সাথে অ্যাপে Authenticator (TOTP) গেট ✓
9a++++. আসল ক্লাউড আইকন (Settings), ভল্ট ক্লাউড-কার্ডের ভেতরে, ছবি/ভিডিওর থাম্বনেল ও grid দৃশ্য ✓
9a+++. Settings-এর ওপরে সব ক্লাউডের মোট স্টোরেজ, ট্যাপ করলে প্রতিটির বিস্তারিত ও progress bar ✓
9a+. Backup/restore (এনক্রিপ্টেড ফাইলে সব সংযোগ export/import) ✓
9b. ইনস্ট্যান্ট প্লেব্যাক (লোকাল streaming সার্ভার + অ্যাপের প্লেয়ার, এনক্রিপ্টেড ভল্টসহ) ✓
9c. Wi-Fi সীমা, গতি সীমা
