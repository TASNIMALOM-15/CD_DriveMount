# Size budget — সম্পূর্ণ সোর্স zip ≤ 30 MB (কঠোর নিয়ম)

এই প্রজেক্টের সব সোর্স কোড একটি zip-এ ৩০MB বা তার নিচে থাকতে হবে (Claude-এ আপলোড সীমা ৩০MB)।
`bash tools/check-size.sh` দিয়ে মাপা যায়; CI-তেও এটি আগে চলে এবং সীমা ছাড়ালে build ব্যর্থ হয়।

## APP LANGUAGE RULE (owner's standing rule — Claude: read this first)
- This is an **English-only app**. Every user-visible string (Text, dialogs, snackbars, notifications, strings.xml, content descriptions) MUST be English.
- Bengali is allowed ONLY in code comments / KDoc / docs. Never put Bengali in anything the user sees in the app UI.
- The owner talks to Claude in Bengali and wants SHORT, token-saving replies; read/edit only the code you need (never the whole codebase).
- Deliver the full project zip every time, named with the next number (e.g. CD-DriveMount-main-98), keeping the zip ≤ 30 MB.

## Claude / যেকোনো ডেভেলপারের জন্য নিয়ম
1. কোনো ফিচার যোগ/বদলানোর আগে ও পরে `tools/check-size.sh` চালাও। ২৪MB পেরোলে সতর্কতা, ৩০MB পেরোলে ব্যর্থ।
2. এগুলো কখনো সোর্সে রাখা যাবে না: `.git` (zip-এ দেওয়ার সময় বাদ), build আউটপুট, `.aar`/`.apk`, `app/libs`, `.gradle`, ক্যাশ, ব্যাকআপ/পুরোনো কপি, লগ, বড় ডেমো মিডিয়া।
3. নতুন লাইব্রেরি/ছবি/ফন্ট/অ্যাসেট যোগ করলে আকার দেখো। বড় ছবি → WebP/ভেক্টর; অব্যবহৃত drawable/string মুছে দাও।
4. `engine/rclone-master.zip` হলো **ছাঁটা** rclone সোর্স (প্রায় ২MB; আসল ১৭.৮MB)। শুধু librclone/gomobile + terabox + dmstream যা import করে সেই প্যাকেজগুলো আছে (`_test.go`, docs, cmd, অব্যবহৃত অংশ বাদ)। রাখা প্যাকেজ বদলাতে (নতুন rclone ফিচার/backend লাগলে) `tools/prune_rclone.py` দিয়ে পূর্ণ rclone সোর্স থেকে আবার ছাঁটো; পূর্ণ সোর্স কখনো commit করো না।
5. Google Photos plugin (আলাদা APK) স্থায়ীভাবে সরানো হয়েছে — ফেরত আনা যাবে না।
6. ফিচারে আকার বাড়ার ঝুঁকি থাকলে ব্যবহারকারীকে আগে জানাও/জিজ্ঞেস করো।
