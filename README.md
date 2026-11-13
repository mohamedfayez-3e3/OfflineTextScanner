# Offline Text Scanner

OfflineTextScanner is a Kotlin/XML Android app for capturing or importing
documents and recognizing Arabic and English text locally. It uses CameraX,
OpenCV, and Tesseract4Android; scanning and OCR do not call remote services.

## Requirements

- JDK 17
- Android SDK Platform 34 and Build-Tools 34.0.0
- Gradle 8.7 (the checked-in Gradle Wrapper downloads this if needed)
- Kotlin 1.9.24 and Android Gradle Plugin 8.5.2

Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to your Android SDK installation.
The checked-in `gradlew` launcher is intended for macOS/Linux; use
`gradlew.bat` on Windows.

## OCR language assets

Place the official `tessdata_fast` LSTM language models at:

```
app/src/main/assets/tessdata/eng.traineddata
app/src/main/assets/tessdata/ara.traineddata
```

The models are bundled in the APK and copied to private app storage on first
use. No language data is downloaded by the app. English (`eng`), Arabic
(`ara`), and mixed (`ara+eng`) modes are available. The included models come
from the official [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast)
repository; its Apache License 2.0 is included alongside the model files.

## Scanner features

- Live CameraX preview with document-boundary overlay, stable-frame auto-capture,
  manual capture, flash control, and a gallery shortcut.
- OpenCV quadrilateral detection, draggable crop corners, perspective correction,
  and a full-image fallback when detection is unavailable.
- Six non-destructive image modes: Original Color, Grayscale, Black and White,
  Adaptive Threshold, Enhanced Contrast, and Document Cleanup.
- On-device Tesseract modes for Arabic, English, or both. OCR results include
  word/line/block boxes when Tesseract returns iterator data.
- Image selection mode supports word taps, line double-taps, block long-press,
  drag selection, pinch zoom, and pan. Editable text mode supports editing,
  copy, and Android Sharesheet sharing.

The original captured/imported file is retained in the app cache and is not
overwritten by perspective correction or enhancement. Preview and OCR bitmaps
are bounded in size to limit memory use.

## Build

From the project directory:

```shell
./gradlew clean test assembleDebug assembleRelease
```

On Windows PowerShell:

```powershell
.\gradlew.bat clean assembleDebug
```

ABI splits are enabled without a universal APK. For most current Android phones
using 64-bit ARM, install `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.
Use the APK matching the device ABI; debug and release APKs are emitted under
`app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`.

## App flow

1. Capture with the live scanner or select an image using Android Photo Picker.
2. Adjust the document corners, compare the original with an enhancement, and
   choose the OCR language.
3. Select recognized text on the scanned image or switch to editable text mode.
   Copy or share the selection or full text.

## Privacy and permissions

Arabic and English Tesseract models are bundled under
`app/src/main/assets/tessdata/` and copied to private app storage when needed.
The app only requests Camera permission when camera scanning is selected;
Photo Picker import does not require storage permission. The app manifest has
no `INTERNET` permission. Runtime OCR and image processing are local.

## Device verification

Automation can verify geometry, OCR structure assembly, coordinate transforms,
and capture-stability logic. Live camera behavior, actual Arabic/English OCR
quality, touch gestures, airplane-mode installation, and release behavior still
require emulator or physical-device verification; this repository does not
claim those checks have already been performed.
