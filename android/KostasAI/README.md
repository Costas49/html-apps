# Kostas AI Android

Lightweight Android WebView wrapper for the bundled Kostas AI HTML app.

## Build

The GitHub Actions workflow at `.github/workflows/build-kostas-ai-apk.yml`
builds a debug APK and uploads it as the `Kostas-AI-debug-APK` artifact.

The Gemini API key is never stored in this repository or compiled into the APK.
It is entered by the user at runtime and stored locally by the HTML application.
