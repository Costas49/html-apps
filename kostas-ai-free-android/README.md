# Kostas AI Free 2.0

Personal Android app for Greek text assistance using the user's Gemini API key.
Android 6+; no native ABI-specific libraries. Distinct package gr.kostas.aifree.
Local assets use WebViewAssetLoader HTTPS origin; no localhost required.
All scripts are scoped, with visible status near Send, automatic answer scrolling,
TXT import/export, request cancellation, timeout, quota waits and local drafts.
The model check lists API models; it does not establish billing status or generation quota.
No PDF or live web search in this version. Google quota and billing terms still apply.
The API key is never included in source or build; optional localStorage persistence
is explicitly disclosed. No analytics or third-party script resources.

Build: Gradle 8.7, JDK 17, assembleDebug. CI verifies mocked browser paths, compiles,
and verifies APK signature. CI does not use a real API key or test on a physical tablet.
The installable development APK uses an ephemeral debug signing key. Subsequent builds
may need reinstall unless stable private signing is configured; export answers first.
