# DSE Swing AI — GitHub Actions Mobile APK Build

এই package-টি মোবাইল থেকেই GitHub Actions cloud build করার জন্য প্রস্তুত। PC/Android Studio প্রয়োজন নেই, তবে একটি GitHub account দরকার।

## মোবাইল থেকে build করার ধাপ

1. GitHub app বা github.com খুলুন এবং নতুন একটি repository তৈরি করুন, যেমন `dse-swing-ai-personal`।
2. এই package-এর সব file repository-তে upload করুন। `android/` folder এবং `.github/workflows/build-apk.yml` অবশ্যই থাকবে।
3. Repository-এর `Actions` tab খুলুন।
4. `Build DSE Swing AI APK` workflow নির্বাচন করুন।
5. `Run workflow` চাপুন।
6. Workflow সফল হলে run-এর `Artifacts` অংশে `dse-swing-ai-debug-apk` download করুন।
7. ZIP খুলে `app-debug.apk` ফোনে install করুন। Android যদি unknown-source warning দেয়, browser/files app-এর জন্য “Allow from this source” সাময়িকভাবে চালু করতে হতে পারে।

## গুরুত্বপূর্ণ

- এটি debug APK; production signing এখনো configured নয়।
- Live DSE sources unofficial/public endpoints; response না এলে app `ERROR`/`INSUFFICIENT_DATA` দেখাবে এবং fake price তৈরি করবে না।
- এই workflow cloud-এ Android SDK/Gradle ব্যবহার করে build করে।
- Android project compileSdk/targetSdk 35 এবং Java 17-এর জন্য configured।
