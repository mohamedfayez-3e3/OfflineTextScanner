# Offline Text Scanner

An Android document scanner that extracts Arabic and English text entirely on
device. Camera capture, image preprocessing, and OCR run locally; the app does
not request Internet access or call a remote service.

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

## Build

From the project directory:

```shell
./gradlew clean assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat clean assembleDebug
```

The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected device
with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## App flow

1. Capture a document with CameraX or choose an image using the Android Photo
   Picker.
2. Confirm the image, select Arabic, English, or mixed OCR, and optionally
   enable local OpenCV grayscale, CLAHE, Gaussian denoising, and adaptive
   thresholding.
3. Review and edit the recognized text, copy it to the clipboard, or share it
   through the Android Sharesheet.

The original scan is not modified. Images are orientation-corrected and scaled
for OCR; if OpenCV cannot initialize or process an image, OCR falls back to the
normalized original.

## Privacy and permissions

The app only requests the Camera permission when camera scanning is selected.
Gallery selection uses the Photo Picker and does not request storage
permissions. The manifest contains no `INTERNET` permission. Camera, image
processing, and OCR are performed on-device.

## Device verification

Test camera permission grant/denial, camera capture, Photo Picker import,
portrait and rotated images, all three OCR language modes, OpenCV fallback,
text editing, clipboard copy, and sharing on a physical Android device.
