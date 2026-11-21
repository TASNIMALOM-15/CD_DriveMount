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
10. "Terabox (TeraPlay)" রিমোট: ndus দিয়ে যোগ, ভিডিও TeraPlay ইঞ্জিনে দ্রুত চলে ✓
9d. ভল্ট স্পিড: এনক্রিপ্টেড লোকাল ক্যাশ (ফোল্ডার-তালিকা + ছবি/ভিডিও থাম্বনেল, Keystore-এ মোড়া কী), স্ট্রিম সার্ভারে ফাইলের শুরু/শেষের মেমরি-ক্যাশ ✓
9e. ধাপ ১: সব ক্লাউডে ফোল্ডার-তালিকার স্থায়ী ইনডেক্স (Room, index.db) + stale-while-revalidate, অপরিবর্তিত তালিকায় UI আবার আঁকা বন্ধ, সাব-ফোল্ডার আগাম আনা ✓
9f. ধাপ ২: সাইডকার মেটাডেটা: APK আপলোডের সময় আইকন/নাম বের করে লোকালে (এনক্রিপ্টেড) ও ক্লাউডের `.dmmeta/pack.dmp`-এ রাখা; ব্রাউজে আসল আইকন ✓
9g. ধাপ ৩: স্ট্রিম সার্ভারে ফাইল-ভিত্তিক শেয়ার্ড ক্যাশ (থাম্বনেল ও প্লেয়ার একই ক্যাশ ব্যবহার করে), মেমরি + সেশন-কী দিয়ে এনক্রিপ্টেড ডিস্ক-ক্যাশ (প্রতিবার অ্যাপ চালুতে মুছে যায়) ✓
9h. ধাপ ৪: সব ক্লাউডে গ্লোবাল সার্চ (লোকাল ইনডেক্সে, অফলাইনেও), ফল ট্যাপ করলে সরাসরি সেই ফোল্ডারে ✓
9i. ধাপ ৫: ছবি/ভিডিওর প্রিভিউ সাইডকারে (`.dmmeta/thumbs.dmp`) + ব্যবহারকারীর স্পষ্ট অনুমতিতে (Settings → Previews) পুরোনো ফাইলের backfill ✓
9j. ধাপ ৬: ছবির মাঝারি প্রিভিউ (১২৮০px, `.dmmeta/prev/`), ফুল-স্ক্রিনে থাম্বনেল → প্রিভিউ → আসল ছবি, Photos ট্যাবের অফলাইন প্রিভিউ ক্যাশ (Wi-Fi, ১GB, সেটিংসে অন/অফ/ক্লিয়ার), EXIF-ঘোরানো থাম্বনেল ফিক্স ✓
9k. ধাপ ৭: CI-তে release APK (debug-এর চেয়ে দ্রুত) + Baseline Profile; multi-thread ডাউনলোড/আপলোড (প্রোভাইডার-ভিত্তিক, Settings-এ সুইচ) ✓
9l. ধাপ ৮: গানের কভার-আর্ট/ট্যাগ সাইডকারে (`.dmmeta/audio.dmp`), Music ট্যাবে কভারসহ তালিকা + অ্যালবাম গ্রিড; Photos/Videos/Music ট্যাব ক্লাউডের সাইডকার পেছনে আমদানি করে ✓
9m. ধাপ ৯: ভিডিও/গানের শুরু-শেষ আগেভাগে ক্যাশে (StreamWarmer): প্লেয়ারের পরের আইটেম + Videos ট্যাবে স্ক্রল থামলে দৃশ্যমান কার্ড; মিটারড নেটওয়ার্ক/ব্যাটারি-সেভারে বন্ধ ✓
9n. ধাপ ১০: "অফলাইনের জন্য রাখো" (ফাইল/ফোল্ডার ফোনে নামানো, Room offline.db + OfflineWorker, ব্রাউজে চিহ্ন, প্লেয়ার/ছবি/খোলা অফলাইন কপি থেকে, Settings-এ সীমা/Wi-Fi/Sync/মুছে ফেলা) ✓
9o. ধাপ ১১: ভল্টের এনক্রিপ্টেড সার্চ ইনডেক্স, অনুমতি-ভিত্তিক পুরো-ড্রাইভ ইনডেক্স (IndexWorker), নাম বদল/কপি/সরালে সাইডকার বজায় (migrate + আকার-মিল), ব্যর্থ সাইডকার/প্রিভিউ আপলোডের রিট্রাই ✓
9p. অফলাইন ডাউনলোড ফিক্স: expedited শুরু (দেরি নেই), আসল অগ্রগতি (ফাইল/%), বাতিলযোগ্য, শেষ ব্যর্থতার কারণ Settings-এ ✓
9q. ব্যবহারকারীর ৬ সমস্যার ফিক্স: ভল্টের বড় ভিডিও (ব্যাকগ্রাউন্ড পড়া সর্বোচ্চ ২ স্লট, প্লেয়ারের জন্য স্লট সংরক্ষিত — Go স্ট্রিম সার্ভার), Clouds ট্যাবে অডিও কভার ও বড় ছবির সাইডকার থাম্বনেল, সার্চ/নোটিফিকেশন থেকে ফাইলে গিয়ে ৩.৫ সেকেন্ড হাইলাইট, অফলাইন নোটিফিকেশনে ট্যাপ-টু-ফাইল ও Cancel, ফোল্ডার-পিকারে ক্লাউড আইকন/ক্যাশ-আগে/ডুপ্লিকেট রোধ/পুরো স্টোরেজ বাছাই বন্ধ/নোটিফিকেশন-ওয়ার্কার, অফলাইন ডাউনলোড সমান্তরাল (৪টি ছোট + ১টি বড় একসঙ্গে) ✓
9r. ভল্ট স্ট্রিমিং: wrapped রিমোটে (crypt) চাংক-সমান্তরালের বদলে প্রতি seek-এ একটি ক্রমিক স্ট্রিম, ৪৫ সেকেন্ড স্টল-টাইমআউট, সাম্প্রতিক ধীর/ব্যর্থ অনুরোধের লগ (প্লেয়ার ২৫ সেকেন্ড আটকে থাকলে কারণসহ দেখায়); Docs/APKs/Favorites ট্যাব চালু (ফোল্ডার-নির্ভর, Photos/Videos/Music-এর একই ভিত্তি); সাইডকার-ছাড়া গানের কভার ব্রাউজে লোকালি বের করা ✓
9c. Wi-Fi সীমা, গতি সীমা
- 9s: Home 'Recent' (played/downloaded/uploaded/offline), folder item counts in Cloud browse, 16:9 video thumbs in list, folders as 2-per-row compact rows in grid
- 9t: Galaxy Drive (step 1): union-of-unions, per-platform pools (mfs) + replicate to every platform (all); card on Cloud tab, setup sheet, capacity = smallest pool, 'Sync copies'
- 9u: Galaxy encrypted vault: 'Create encrypted vault' on the Galaxy card (preselects Galaxy as base, recovery code flow), vault chips with open/rename/remove
- 9v: Galaxy replica badges (✓ 2/2 / ⚠ 1/2) per file in Galaxy and Galaxy-vault folders (lists each platform pool in the background; hidden crypt 'view' remotes dmgv*)
- 9w: Galaxy trash (30 days): delete in Galaxy/Galaxy-vault moves to hidden .galaxy-trash in all clouds; Trash sheet (restore / delete forever / empty); auto-purge of expired items
- 9x: Galaxy import wizard: copy any folder (cloud/phone) into Galaxy or a Galaxy vault as 'Imported/<account>/<folder>' via Transfers (copy only, explicit consent)

## সোর্স আকার (গুরুত্বপূর্ণ)
সম্পূর্ণ সোর্স zip সবসময় ≤ 30MB রাখতে হবে — বিস্তারিত `SIZE_BUDGET.md`। `bash tools/check-size.sh` দিয়ে মাপুন।
`engine/rclone-master.zip` এখন **ছাঁটা** rclone সোর্স (ব্যবহৃত প্যাকেজ ছাড়া সব বাদ); GitHub Actions আগের মতোই unzip করে।
Google Photos plugin (আলাদা APK) সরানো হয়েছে।

- ✓ আপলোড চলাকালীন ফাইলটি গন্তব্য ফোল্ডারের তালিকায় ধূসর অবস্থায় দেখা যায়; আপলোডের শতাংশ অনুযায়ী বাম থেকে ডানে রঙিন হয় (ui/browser/UploadGhostUi.kt, BrowserViewModel.uploadGhosts)
- ✓ পরের ফাইলের প্রস্তুতি (কপি/থাম্বনেল/seek sheet) বর্তমান ফাইল আপলোডের সময়ই; ব্যর্থ প্রিভিউ আপলোডের সংখ্যা + Retry (Transfers স্ক্রিন)
