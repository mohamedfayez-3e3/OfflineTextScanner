# Offline Text Scanner – Complete Android Project & Phase-by-Phase Implementation

## 1. Workspace Inspection & Dependency Compatibility Report

Before generating source code, all required libraries were audited against `minSdk = 23`, `compileSdk = 34`, and Kotlin `1.9.24`:

| Component | Selected Artifact / Coordinate | Compatibility & Maintenance Status | Official Source | 
| ----- | ----- | ----- | ----- | 
| **Android Gradle Plugin** | `com.android.application:8.5.2` | Stable AGP 8.5 toolchain with JDK 17 bytecode target and ViewBinding support | [Android Developers AGP](https://developer.android.com/build/releases/gradle-plugin) | 
| **Kotlin Android** | `org.jetbrains.kotlin.android:1.9.24` | Stable Kotlin 1.9 compiler compatible with AGP 8.5 and Coroutines `1.8.1` | [Kotlin Releases](https://kotlinlang.org/docs/releases.html) | 
| **Tesseract OCR Engine** | `cz.adaptech.tesseract4android:tesseract4android-openmp:4.9.0` | **Replaces deprecated `rmtheis:tess-two`** ([Issue #196](https://github.com/rmtheis/tess-two/issues/196)), which is unmaintained and stuck on Tesseract 3.05/JCenter. `Tesseract4Android` compiles Tesseract 5.5.1 + Leptonica 1.85.0 with OpenMP multi-threading and supports `minSdk = 21`. | [adaptech-cz/Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) | 
| **OpenCV Image Processing** | `org.opencv:opencv:4.10.0` | Official Maven Central Android AAR package (`OpenCVLoader.initLocal()`), eliminating manual `:sdk` module imports and supporting `minSdk = 21`. | [Maven Central: org.opencv:opencv:4.10.0](https://mvnrepository.com/artifact/org.opencv/opencv/4.10.0) | 
| **CameraX** | `androidx.camera:camera-{core,camera2,lifecycle,view}:1.3.4` | Stable Jetpack CameraX release supporting `minSdk = 21`, lifecycle-bound `PreviewView`, and high-resolution `ImageCapture`. | [Android Developers CameraX](https://developer.android.com/jetpack/androidx/releases/camera) | 
| **Android Photo Picker** | `androidx.activity:activity-ktx:1.9.3` | Provides `ActivityResultContracts.PickVisualMedia` for zero-permission gallery image selection across Android versions. | [Android Developers Activity](https://developer.android.com/jetpack/androidx/releases/activity) | 
| **LSTM Language Data** | `eng.traineddata` & `ara.traineddata` | Integer-quantized LSTM models for fast local offline English (`eng`), Arabic (`ara`), and mixed (`ara+eng`) recognition. | [tesseract-ocr/tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast) | 

## 2. Complete Project Structure & File Responsibilities

| File Path | Architectural Layer | Core Responsibility | 
| ----- | ----- | ----- | 
| `settings.gradle.kts` | Build Config | Declares Google, Maven Central, and JitPack (`https://jitpack.io`) repositories and includes `:app`. | 
| `build.gradle.kts` | Build Config | Declares root Android Application (`8.5.2`) and Kotlin Android (`1.9.24`) plugins. | 
| `app/build.gradle.kts` | Build Config | Configures `minSdk = 23`, `targetSdk = 34`, `viewBinding = true`, `noCompress += "traineddata"`, and all verified dependencies. | 
| `app/src/main/AndroidManifest.xml` | Manifest | Declares `CAMERA` permission (with optional hardware feature), registers the 4 activities, enables `android:supportsRtl="true"`, and **omits `INTERNET` permission**. | 
| `MainActivity.kt` | UI (Phase 1) | Initializes the Home Screen, displays the offline badge and empty state, requests camera permission, and launches Camera or Photo Picker. | 
| `activities/CameraActivity.kt` | UI (Phase 2) | Hosts the full-screen CameraX `PreviewView`, binds camera lifecycle via `CameraManager`, and captures document photos via `ImageCaptureHandler`. | 
| `activities/PreviewActivity.kt` | UI (Phase 4–6) | Displays the captured/selected image, lets the user pick `ara+eng`, `eng`, or `ara` and toggle OpenCV enhancement, and runs preprocessing + OCR asynchronously. | 
| `activities/ResultActivity.kt` | UI (Phase 7) | Displays editable extracted text with bidirectional RTL/LTR alignment (`textDirection="anyRtl"`), confidence metadata, Copy All, Share, and Scan Another actions. | 
| `camera/CameraManager.kt` | Camera (Phase 2) | Initializes `ProcessCameraProvider`, binds `Preview` and `ImageCapture` (`CAPTURE_MODE_MAXIMIZE_QUALITY`) to the lifecycle owner, and manages the camera executor. | 
| `camera/ImageCaptureHandler.kt` | Camera (Phase 2) | Captures still images from CameraX into app-private cache files and returns the saved file `Uri`. | 
| `gallery/GalleryManager.kt` | Gallery (Phase 3) | Wraps `ActivityResultContracts.PickVisualMedia` (`ImageOnly`) to select gallery images without requesting storage permissions. | 
| `imageprocessing/ImageUtils.kt` | Image Processing (Phase 5) | Decodes bitmaps safely with `inSampleSize`, reads EXIF orientation tags via `ExifInterface`, corrects rotation, and scales down oversized images (`<= 2200px`). | 
| `imageprocessing/ImageEnhancer.kt` | Image Processing (Phase 5) | Converts bitmaps to OpenCV `Mat`, applies grayscale conversion, CLAHE contrast enhancement, 3x3 Gaussian denoising, and adaptive Gaussian thresholding. | 
| `imageprocessing/ImagePreprocessor.kt` | Image Processing (Phase 5) | Initializes OpenCV locally via `OpenCVLoader.initLocal()` and runs the non-destructive preprocessing pipeline without altering the original file. | 
| `ocr/OCRConfig.kt` | OCR (Phase 6) | Defines language constants (`eng`, `ara`, `ara+eng`), required `.traineddata` filenames, LSTM engine mode (`OEM_LSTM_ONLY`), and page segmentation mode (`PSM_AUTO`). | 
| `ocr/OCRResult.kt` | OCR (Phase 6) | Sealed class modeling `Success(extractedText, meanConfidence, languageCode)` and typed `Error` states (`MISSING_TESSDATA`, `INIT_FAILED`, `EMPTY_RESULT`, etc.). | 
| `ocr/OCRProcessor.kt` | OCR (Phase 6) | Extracts `eng.traineddata` and `ara.traineddata` to internal storage, initializes `TessBaseAPI`, and executes OCR on `Dispatchers.Default`. | 
| `utils/PermissionHelper.kt` | Utilities (Phase 1) | Encapsulates runtime `Manifest.permission.CAMERA` checks and `ActivityResultContracts.RequestPermission` callbacks. | 
| `utils/FileHelper.kt` | Utilities (Phase 1 & 6) | Checks available internal storage, creates temporary scan files, copies gallery `Uri` streams, and copies `.traineddata` assets into `<filesDir>/tesseract/tessdata/`. | 
| `utils/ClipboardHelper.kt` | Utilities (Phase 7) | Copies extracted text to Android `ClipboardManager` with error handling. | 
| `utils/ShareHelper.kt` | Utilities (Phase 7) | Launches the Android Sharesheet (`Intent.ACTION_SEND`) for plain-text sharing. | 
| `models/ScannedDocument.kt` | Domain Model (Phase 1) | Data class holding `imageUri`, `extractedText`, `languageCode`, `confidenceScore`, and `timestampMillis`. | 

## 3. Phase 1 Implementation: Gradle Configuration, Manifest, Resources & Home Screen

### `settings.gradle.kts`

```
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "OfflineTextScanner"
include(":app")

```

### `build.gradle.kts`

```
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

```

### `gradle.properties`

```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true

```

### `gradle/wrapper/gradle-wrapper.properties`

```
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists

```

### `app/proguard-rules.pro`

```
# Offline Text Scanner ProGuard / R8 rules
-keep class com.googlecode.tesseract.android.** { *; }
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

```

### `app/build.gradle.kts`

```
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.offlinetextscanner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.offlinetextscanner"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    androidResources {
        noCompress += listOf("traineddata")
    }
}

dependencies {
    val cameraxVersion = "1.3.4"

    // Core AndroidX & UI (XML Views, Material Components, ConstraintLayout)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Kotlin Coroutines for non-blocking background preprocessing & OCR
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // CameraX
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // OpenCV Official Android AAR (Maven Central)
    implementation("org.opencv:opencv:4.10.0")

    // Tesseract4Android (Actively maintained CMake fork of tess-two with Tesseract 5.5.1 & OpenMP)
    implementation("cz.adaptech.tesseract4android:tesseract4android-openmp:4.9.0")
}

```

### `app/src/main/AndroidManifest.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Strictly Offline: No network or internet permissions are requested -->
    <uses-feature
        android:name="android.hardware.camera.any"
        android:required="false" />

    <uses-permission android:name="android.permission.CAMERA" />

    <application
        android:allowBackup="true"
        android:icon="@drawable/logo_background"
        android:label="@string/app_name"
        android:roundIcon="@drawable/logo_background"
        android:supportsRtl="true"
        android:theme="@style/Theme.OfflineTextScanner">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".activities.CameraActivity"
            android:exported="false"
            android:screenOrientation="portrait" />

        <activity
            android:name=".activities.PreviewActivity"
            android:exported="false" />

        <activity
            android:name=".activities.ResultActivity"
            android:exported="false"
            android:windowSoftInputMode="adjustResize" />

    </application>

</manifest>

```

### `app/src/main/res/values/colors.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary_blue">#1565C0</color>
    <color name="primary_blue_dark">#0D47A1</color>
    <color name="primary_blue_light">#E3F2FD</color>
    <color name="accent_blue">#1E88E5</color>
    <color name="background_white">#FFFFFF</color>
    <color name="surface_gray">#F8FAFC</color>
    <color name="text_primary">#0F172A</color>
    <color name="text_secondary">#475569</color>
    <color name="offline_green_bg">#E8F5E9</color>
    <color name="offline_green_text">#2E7D32</color>
    <color name="error_red">#C62828</color>
    <color name="divider_gray">#E2E8F0</color>
    <color name="black_overlay">#B3000000</color>
</resources>

```

### `app/src/main/res/values/dimens.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <dimen name="spacing_xs">4dp</dimen>
    <dimen name="spacing_sm">8dp</dimen>
    <dimen name="spacing_md">16dp</dimen>
    <dimen name="spacing_lg">24dp</dimen>
    <dimen name="spacing_xl">32dp</dimen>
    <dimen name="button_corner_radius">12dp</dimen>
    <dimen name="card_corner_radius">16dp</dimen>
    <dimen name="logo_size">72dp</dimen>
    <dimen name="button_height">56dp</dimen>
</resources>

```

### `app/src/main/res/values/strings.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Offline Text Scanner</string>
    <string name="app_description">Scan printed documents with your camera or pick an image from your gallery to extract Arabic and English text 100% offline on your device.</string>
    <string name="offline_badge_text">100% Offline • Local Tesseract OCR (Arabic &amp; English)</string>
    <string name="btn_scan_camera">Scan with Camera</string>
    <string name="btn_import_gallery">Import from Gallery</string>
    <string name="empty_state_title">No Document Scanned Yet</string>
    <string name="empty_state_subtitle">Capture a page with your camera or select an image from your gallery above to extract editable text locally.</string>

    <!-- Camera Screen -->
    <string name="camera_capture">Capture Document</string>
    <string name="camera_close">Close</string>
    <string name="camera_initializing">Initializing camera preview…</string>

    <!-- Preview Screen -->
    <string name="preview_title">Verify Document Image</string>
    <string name="language_label">OCR Language Mode:</string>
    <string name="lang_eng_ara">Arabic + English (Mixed)</string>
    <string name="lang_eng">English Only (eng)</string>
    <string name="lang_ara">Arabic Only (ara)</string>
    <string name="checkbox_preprocess">Enhance image with OpenCV before OCR</string>
    <string name="btn_retake">Retake / Reselect</string>
    <string name="btn_confirm_ocr">Confirm &amp; Extract Text</string>
    <string name="processing_ocr">Preprocessing image with OpenCV &amp; running local Tesseract OCR…</string>

    <!-- Result Screen -->
    <string name="result_title">Extracted Text</string>
    <string name="result_hint">Extracted text will appear here. You can edit or select any portion.</string>
    <string name="btn_copy_all">Copy All</string>
    <string name="btn_share">Share Text</string>
    <string name="btn_scan_another">Scan Another Document</string>
    <string name="text_copied_toast">Text copied to clipboard</string>
    <string name="share_chooser_title">Share Extracted Text</string>

    <!-- Error Messages -->
    <string name="err_camera_permission_denied">Camera permission is required to scan documents. Please grant permission in Settings.</string>
    <string name="err_camera_unavailable">Camera hardware is unavailable or could not be initialized on this device.</string>
    <string name="err_gallery_canceled">Image selection was canceled.</string>
    <string name="err_invalid_image">The selected image file is invalid or could not be opened.</string>
    <string name="err_image_decode">Failed to decode bitmap from the provided image file.</string>
    <string name="err_ocr_init">Failed to initialize local Tesseract OCR engine.</string>
    <string name="err_missing_tessdata">Missing OCR language files (%1$s) in assets/tessdata/. Please bundle eng.traineddata and ara.traineddata.</string>
    <string name="err_unsupported_language">Selected language code (%1$s) is not supported.</string>
    <string name="err_ocr_failed">OCR processing encountered an unexpected error: %1$s</string>
    <string name="err_empty_ocr_result">No readable text was detected in the image. Try retaking with clearer lighting.</string>
    <string name="err_clipboard_failed">Unable to copy text to the Android clipboard.</string>
    <string name="err_insufficient_storage">Insufficient local storage to save temporary image or OCR language data.</string>
</resources>

```

### `app/src/main/res/values/themes.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.OfflineTextScanner" parent="Theme.MaterialComponents.Light.NoActionBar">
        <item name="colorPrimary">@color/primary_blue</item>
        <item name="colorPrimaryVariant">@color/primary_blue_dark</item>
        <item name="colorOnPrimary">@color/background_white</item>
        <item name="colorSecondary">@color/accent_blue</item>
        <item name="android:statusBarColor">@color/primary_blue_dark</item>
        <item name="android:windowBackground">@color/background_white</item>
    </style>
</resources>

```

### `app/src/main/res/drawable/logo_background.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/primary_blue_light" />
            <corners android:radius="20dp" />
            <size
                android:width="72dp"
                android:height="72dp" />
        </shape>
    </item>
    <item
        android:bottom="16dp"
        android:left="16dp"
        android:right="16dp"
        android:top="16dp">
        <vector
            android:width="40dp"
            android:height="40dp"
            android:viewportHeight="24"
            android:viewportWidth="24">
            <path
                android:fillColor="@color/primary_blue"
                android:pathData="M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8C6.9,2 6,2.9 6,4v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4C22,2.9 21.1,2 20,2zM19,9H9V7h10V9zM15,13H9v-2h6V13zM19,5H9V4h10V5z" />
        </vector>
    </item>
</layer-list>

```

### `app/src/main/res/drawable/offline_background.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/offline_green_bg" />
    <corners android:radius="24dp" />
    <stroke
        android:width="1dp"
        android:color="@color/offline_green_text" />
    <padding
        android:bottom="6dp"
        android:left="14dp"
        android:right="14dp"
        android:top="6dp" />
</shape>

```

### `app/src/main/res/drawable/button_background.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/primary_blue" />
    <corners android:radius="@dimen/button_corner_radius" />
</shape>

```

### `app/src/main/res/layout/activity_main.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_white"
    android:fillViewport="true">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="@dimen/spacing_lg">

        <ImageView
            android:id="@+id/ivAppLogo"
            android:layout_width="@dimen/logo_size"
            android:layout_height="@dimen/logo_size"
            android:layout_marginTop="@dimen/spacing_md"
            android:contentDescription="@string/app_name"
            android:src="@drawable/logo_background"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <TextView
            android:id="@+id/tvAppName"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/spacing_md"
            android:gravity="center"
            android:text="@string/app_name"
            android:textColor="@color/text_primary"
            android:textSize="24sp"
            android:textStyle="bold"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/ivAppLogo" />

        <TextView
            android:id="@+id/tvOfflineIndicator"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/spacing_sm"
            android:background="@drawable/offline_background"
            android:text="@string/offline_badge_text"
            android:textColor="@color/offline_green_text"
            android:textSize="12sp"
            android:textStyle="bold"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/tvAppName" />

        <TextView
            android:id="@+id/tvAppDescription"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/spacing_md"
            android:gravity="center"
            android:lineSpacingExtra="4dp"
            android:text="@string/app_description"
            android:textColor="@color/text_secondary"
            android:textSize="14sp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/tvOfflineIndicator" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnScanCamera"
            android:layout_width="0dp"
            android:layout_height="@dimen/button_height"
            android:layout_marginTop="@dimen/spacing_xl"
            android:background="@drawable/button_background"
            android:text="@string/btn_scan_camera"
            android:textAllCaps="false"
            android:textColor="@color/background_white"
            android:textSize="16sp"
            app:backgroundTint="@color/primary_blue"
            app:cornerRadius="@dimen/button_corner_radius"
            app:icon="@android:drawable/ic_menu_camera"
            app:iconGravity="textStart"
            app:iconPadding="@dimen/spacing_sm"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/tvAppDescription" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnImportGallery"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"
            android:layout_width="0dp"
            android:layout_height="@dimen/button_height"
            android:layout_marginTop="@dimen/spacing_md"
            android:text="@string/btn_import_gallery"
            android:textAllCaps="false"
            android:textColor="@color/primary_blue"
            android:textSize="16sp"
            app:cornerRadius="@dimen/button_corner_radius"
            app:icon="@android:drawable/ic_menu_gallery"
            app:iconGravity="textStart"
            app:iconPadding="@dimen/spacing_sm"
            app:iconTint="@color/primary_blue"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/btnScanCamera"
            app:strokeColor="@color/primary_blue"
            app:strokeWidth="2dp" />

        <LinearLayout
            android:id="@+id/layoutEmptyState"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/spacing_xl"
            android:background="@color/surface_gray"
            android:gravity="center"
            android:orientation="vertical"
            android:padding="@dimen/spacing_lg"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/btnImportGallery">

            <TextView
                android:id="@+id/tvEmptyStateTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/empty_state_title"
                android:textColor="@color/text_primary"
                android:textSize="16sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tvEmptyStateSubtitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_xs"
                android:gravity="center"
                android:text="@string/empty_state_subtitle"
                android:textColor="@color/text_secondary"
                android:textSize="13sp" />
        </LinearLayout>

    </androidx.constraintlayout.widget.ConstraintLayout>
</ScrollView>

```

### `app/src/main/java/com/example/offlinetextscanner/models/ScannedDocument.kt`

```
package com.example.offlinetextscanner.models

import android.net.Uri

/**
 * Represents a scanned document including its source image URI, extracted OCR text,
 * recognition language code, confidence score, and capture timestamp.
 */
data class ScannedDocument(
    val imageUri: Uri,
    val extractedText: String,
    val languageCode: String,
    val confidenceScore: Int = 0,
    val timestampMillis: Long = System.currentTimeMillis()
)

```

### `app/src/main/java/com/example/offlinetextscanner/utils/PermissionHelper.kt`

```
package com.example.offlinetextscanner.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Handles runtime CAMERA permission checks and requests.
 * Note: Android Photo Picker does not require storage permissions.
 */
class PermissionHelper(
    private val activity: ComponentActivity,
    private val onPermissionResult: (Boolean) -> Unit
) {
    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            onPermissionResult(isGranted)
        }

    fun hasCameraPermission(context: Context = activity): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCameraPermission() {
        if (hasCameraPermission()) {
            onPermissionResult(true)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

```

### `app/src/main/java/com/example/offlinetextscanner/utils/FileHelper.kt`

```
package com.example.offlinetextscanner.utils

import android.content.Context
import android.net.Uri
import com.example.offlinetextscanner.ocr.OCRConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Manages temporary image files in cache storage and copies bundled Tesseract
 * traineddata files from assets/tessdata/ to internal app storage on first run.
 */
object FileHelper {

    private const val TESSERACT_DIR_NAME = "tesseract"
    private const val TESSDATA_SUBDIR_NAME = "tessdata"
    private const val MIN_FREE_SPACE_BYTES = 15L * 1024L * 1024L // 15 MB safety floor

    fun hasSufficientStorage(context: Context): Boolean {
        return context.filesDir.usableSpace >= MIN_FREE_SPACE_BYTES
    }

    @Throws(IOException::class)
    fun createTempImageFile(context: Context, prefix: String = "SCAN_"): File {
        if (!hasSufficientStorage(context)) {
            throw IOException("Insufficient internal storage space.")
        }
        val cacheDir = File(context.cacheDir, "scans").apply {
            if (!exists()) mkdirs()
        }
        return File.createTempFile(prefix, ".jpg", cacheDir)
    }

    @Throws(IOException::class)
    fun copyUriToTempFile(context: Context, sourceUri: Uri): File {
        val destFile = createTempImageFile(context, "GALLERY_")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Unable to open input stream for selected URI.")
        if (destFile.length() == 0L) {
            destFile.delete()
            throw IOException("Selected image file is empty.")
        }
        return destFile
    }

    /**
     * Ensures that eng.traineddata and ara.traineddata exist inside
     * <filesDir>/tesseract/tessdata/ so TessBaseAPI can read them directly.
     * Returns the parent base path (<filesDir>/tesseract) required by TessBaseAPI.init().
     */
    @Throws(IOException::class)
    fun prepareTessDataDirectory(context: Context): String {
        if (!hasSufficientStorage(context)) {
            throw IOException("Insufficient internal storage to extract OCR traineddata.")
        }
        val baseDir = File(context.filesDir, TESSERACT_DIR_NAME)
        val tessDataDir = File(baseDir, TESSDATA_SUBDIR_NAME)
        if (!tessDataDir.exists() && !tessDataDir.mkdirs()) {
            throw IOException("Could not create tessdata directory at ${tessDataDir.absolutePath}")
        }

        val assetFiles = context.assets.list(TESSDATA_SUBDIR_NAME)?.toSet() ?: emptySet()
        val missingFiles = mutableListOf<String>()

        for (fileName in OCRConfig.REQUIRED_TRAINEDDATA_FILES) {
            val targetFile = File(tessDataDir, fileName)
            if (targetFile.exists() && targetFile.length() > 0L) {
                continue
            }
            if (!assetFiles.contains(fileName)) {
                missingFiles.add(fileName)
                continue
            }
            context.assets.open("$TESSDATA_SUBDIR_NAME/$fileName").use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        if (missingFiles.isNotEmpty()) {
            throw MissingTessDataException(missingFiles)
        }

        return baseDir.absolutePath
    }

    class MissingTessDataException(val missingFiles: List<String>) :
        IOException("Missing OCR language files: ${missingFiles.joinToString(", ")}")
}

```

### `app/src/main/java/com/example/offlinetextscanner/utils/ClipboardHelper.kt`

```
package com.example.offlinetextscanner.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Copies extracted or edited OCR text to the system Clipboard.
 */
object ClipboardHelper {

    fun copyToClipboard(context: Context, text: String, label: String = "Extracted OCR Text"): Boolean {
        if (text.isBlank()) return false
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }
}

```

### `app/src/main/java/com/example/offlinetextscanner/utils/ShareHelper.kt`

```
package com.example.offlinetextscanner.utils

import android.content.Context
import android.content.Intent
import com.example.offlinetextscanner.R

/**
 * Shares extracted OCR text using the native Android Sharesheet.
 */
object ShareHelper {

    fun shareText(context: Context, text: String) {
        if (text.isBlank()) return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooserIntent = Intent.createChooser(
            sendIntent,
            context.getString(R.string.share_chooser_title)
        )
        context.startActivity(chooserIntent)
    }
}

```

### `app/src/main/java/com/example/offlinetextscanner/MainActivity.kt`

```
package com.example.offlinetextscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.offlinetextscanner.activities.CameraActivity
import com.example.offlinetextscanner.activities.PreviewActivity
import com.example.offlinetextscanner.databinding.ActivityMainBinding
import com.example.offlinetextscanner.gallery.GalleryManager
import com.example.offlinetextscanner.utils.FileHelper
import com.example.offlinetextscanner.utils.PermissionHelper

/**
 * Home screen activity providing offline status indication, camera scan launch,
 * and zero-permission Android Photo Picker gallery import.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var galleryManager: GalleryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionHelper = PermissionHelper(this) { isGranted ->
            if (isGranted) {
                openCameraActivity()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.err_camera_permission_denied),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        galleryManager = GalleryManager(this) { selectedUri ->
            handleGallerySelection(selectedUri)
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnScanCamera.setOnClickListener {
            permissionHelper.requestCameraPermission()
        }

        binding.btnImportGallery.setOnClickListener {
            galleryManager.openPhotoPicker()
        }
    }

    private fun openCameraActivity() {
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }

    private fun handleGallerySelection(selectedUri: Uri?) {
        if (selectedUri == null) {
            Toast.makeText(
                this,
                getString(R.string.err_gallery_canceled),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val localFile = FileHelper.copyUriToTempFile(this, selectedUri)
            val intent = Intent(this, PreviewActivity::class.java).apply {
                putExtra(PreviewActivity.EXTRA_IMAGE_PATH, localFile.absolutePath)
                putExtra(PreviewActivity.EXTRA_SOURCE_TYPE, PreviewActivity.SOURCE_GALLERY)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.err_invalid_image),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

```

## 4. Phases 2–7 Implementation: CameraX, Photo Picker, Preview, OpenCV Preprocessing, Local Tesseract OCR & Result Screen

### `app/src/main/java/com/example/offlinetextscanner/camera/CameraManager.kt`

```
package com.example.offlinetextscanner.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Configures and binds CameraX Preview and ImageCapture use cases to the activity lifecycle.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onCameraError: (Throwable) -> Unit
) {
    var imageCapture: ImageCapture? = null
        private set

    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun startCamera(onReady: () -> Unit = {}) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                if (!cameraProvider.hasCamera(cameraSelector)) {
                    onCameraError(IllegalStateException("Back camera is not available on this device."))
                    return@addListener
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                onReady()
            } catch (e: Exception) {
                onCameraError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}

```

### `app/src/main/java/com/example/offlinetextscanner/camera/ImageCaptureHandler.kt`

```
package com.example.offlinetextscanner.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.example.offlinetextscanner.utils.FileHelper
import java.io.File

/**
 * Handles capturing a high-quality still image from CameraX and saving it to a local cache file.
 */
class ImageCaptureHandler(private val context: Context) {

    fun captureImage(
        imageCapture: ImageCapture?,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val captureUseCase = imageCapture ?: run {
            onError("Camera is not ready for capture.")
            return
        }

        val photoFile: File = try {
            FileHelper.createTempImageFile(context, "CAMERA_")
        } catch (e: Exception) {
            onError(e.message ?: "Failed to create image file.")
            return
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        captureUseCase.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSuccess(Uri.fromFile(photoFile))
                }

                override fun onError(exception: ImageCaptureException) {
                    photoFile.delete()
                    onError(exception.message ?: "Image capture failed.")
                }
            }
        )
    }
}

```

### `app/src/main/res/layout/activity_camera.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/black">

    <androidx.camera.view.PreviewView
        android:id="@+id/viewFinder"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintBottom_toTopOf="@id/cameraControlsContainer"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <ProgressBar
        android:id="@+id/cameraProgressBar"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:visibility="gone"
        app:layout_constraintBottom_toBottomOf="@id/viewFinder"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <LinearLayout
        android:id="@+id/cameraControlsContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:background="@color/black_overlay"
        android:gravity="center"
        android:orientation="horizontal"
        android:padding="@dimen/spacing_lg"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnCloseCamera"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"
            android:layout_width="wrap_content"
            android:layout_height="@dimen/button_height"
            android:layout_marginEnd="@dimen/spacing_md"
            android:text="@string/camera_close"
            android:textAllCaps="false"
            android:textColor="@color/background_white"
            app:cornerRadius="@dimen/button_corner_radius"
            app:strokeColor="@color/background_white" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnCaptureImage"
            android:layout_width="0dp"
            android:layout_height="@dimen/button_height"
            android:layout_weight="1"
            android:text="@string/camera_capture"
            android:textAllCaps="false"
            android:textColor="@color/background_white"
            app:backgroundTint="@color/primary_blue"
            app:cornerRadius="@dimen/button_corner_radius"
            app:icon="@android:drawable/ic_menu_camera"
            app:iconGravity="textStart" />
    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>

```

### `app/src/main/java/com/example/offlinetextscanner/activities/CameraActivity.kt`

```
package com.example.offlinetextscanner.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.offlinetextscanner.R
import com.example.offlinetextscanner.camera.CameraManager
import com.example.offlinetextscanner.camera.ImageCaptureHandler
import com.example.offlinetextscanner.databinding.ActivityCameraBinding
import java.io.File

/**
 * Displays the live CameraX preview and captures a document image to local cache storage.
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var imageCaptureHandler: ImageCaptureHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageCaptureHandler = ImageCaptureHandler(this)
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = binding.viewFinder,
            onCameraError = {
                Toast.makeText(
                    this,
                    getString(R.string.err_camera_unavailable),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        )

        binding.cameraProgressBar.visibility = View.VISIBLE
        cameraManager.startCamera {
            binding.cameraProgressBar.visibility = View.GONE
        }

        binding.btnCloseCamera.setOnClickListener {
            finish()
        }

        binding.btnCaptureImage.setOnClickListener {
            captureDocumentImage()
        }
    }

    private fun captureDocumentImage() {
        binding.btnCaptureImage.isEnabled = false
        binding.cameraProgressBar.visibility = View.VISIBLE

        imageCaptureHandler.captureImage(
            imageCapture = cameraManager.imageCapture,
            onSuccess = { savedUri ->
                binding.cameraProgressBar.visibility = View.GONE
                binding.btnCaptureImage.isEnabled = true
                val filePath = savedUri.path ?: return@captureImage
                val intent = Intent(this, PreviewActivity::class.java).apply {
                    putExtra(PreviewActivity.EXTRA_IMAGE_PATH, File(filePath).absolutePath)
                    putExtra(PreviewActivity.EXTRA_SOURCE_TYPE, PreviewActivity.SOURCE_CAMERA)
                }
                startActivity(intent)
                finish()
            },
            onError = { errorMsg ->
                binding.cameraProgressBar.visibility = View.GONE
                binding.btnCaptureImage.isEnabled = true
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.shutdown()
    }
}

```

### `app/src/main/java/com/example/offlinetextscanner/gallery/GalleryManager.kt`

```
package com.example.offlinetextscanner.gallery

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Manages the Android Photo Picker (ActivityResultContracts.PickVisualMedia)
 * to let users select an image without requesting storage permissions.
 */
class GalleryManager(
    activity: ComponentActivity,
    private val onImagePicked: (Uri?) -> Unit
) {
    private val pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest> =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            onImagePicked(uri)
        }

    fun openPhotoPicker() {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}

```

### `app/src/main/res/layout/activity_preview.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_white"
    android:padding="@dimen/spacing_md">

    <TextView
        android:id="@+id/tvPreviewTitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:text="@string/preview_title"
        android:textColor="@color/text_primary"
        android:textSize="20sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <ImageView
        android:id="@+id/ivPreviewImage"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginTop="@dimen/spacing_md"
        android:layout_marginBottom="@dimen/spacing_md"
        android:background="@color/surface_gray"
        android:contentDescription="@string/preview_title"
        android:scaleType="fitCenter"
        app:layout_constraintBottom_toTopOf="@id/tvLanguageLabel"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/tvPreviewTitle" />

    <TextView
        android:id="@+id/tvLanguageLabel"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:text="@string/language_label"
        android:textColor="@color/text_primary"
        android:textSize="14sp"
        android:textStyle="bold"
        app:layout_constraintBottom_toTopOf="@id/rgLanguageMode"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <RadioGroup
        android:id="@+id/rgLanguageMode"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        app:layout_constraintBottom_toTopOf="@id/cbEnablePreprocessing"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent">

        <RadioButton
            android:id="@+id/rbLangMixed"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:checked="true"
            android:text="@string/lang_eng_ara" />

        <RadioButton
            android:id="@+id/rbLangEng"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/lang_eng" />

        <RadioButton
            android:id="@+id/rbLangAra"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/lang_ara" />
    </RadioGroup>

    <CheckBox
        android:id="@+id/cbEnablePreprocessing"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginBottom="@dimen/spacing_sm"
        android:checked="true"
        android:text="@string/checkbox_preprocess"
        android:textColor="@color/text_secondary"
        app:layout_constraintBottom_toTopOf="@id/previewButtonsRow"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <LinearLayout
        android:id="@+id/previewButtonsRow"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnRetake"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"
            android:layout_width="0dp"
            android:layout_height="@dimen/button_height"
            android:layout_marginEnd="@dimen/spacing_sm"
            android:layout_weight="1"
            android:text="@string/btn_retake"
            android:textAllCaps="false"
            android:textColor="@color/primary_blue"
            app:cornerRadius="@dimen/button_corner_radius"
            app:strokeColor="@color/primary_blue" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnConfirmOcr"
            android:layout_width="0dp"
            android:layout_height="@dimen/button_height"
            android:layout_marginStart="@dimen/spacing_sm"
            android:layout_weight="1.3"
            android:text="@string/btn_confirm_ocr"
            android:textAllCaps="false"
            android:textColor="@color/background_white"
            app:backgroundTint="@color/primary_blue"
            app:cornerRadius="@dimen/button_corner_radius" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/loadingOverlay"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:background="@color/black_overlay"
        android:clickable="true"
        android:focusable="true"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="@dimen/spacing_lg"
        android:visibility="gone"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent">

        <ProgressBar
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

        <TextView
            android:id="@+id/tvProcessingStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/spacing_md"
            android:gravity="center"
            android:text="@string/processing_ocr"
            android:textColor="@color/background_white"
            android:textSize="15sp" />
    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>

```

### `app/src/main/java/com/example/offlinetextscanner/activities/PreviewActivity.kt`

```
package com.example.offlinetextscanner.activities

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.offlinetextscanner.R
import com.example.offlinetextscanner.databinding.ActivityPreviewBinding
import com.example.offlinetextscanner.imageprocessing.ImagePreprocessor
import com.example.offlinetextscanner.imageprocessing.ImageUtils
import com.example.offlinetextscanner.models.ScannedDocument
import com.example.offlinetextscanner.ocr.OCRConfig
import com.example.offlinetextscanner.ocr.OCRProcessor
import com.example.offlinetextscanner.ocr.OCRResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Displays the captured or selected image, allows the user to choose Arabic/English OCR settings,
 * runs OpenCV preprocessing and local Tesseract OCR, and navigates to ResultActivity.
 */
class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_SOURCE_TYPE = "extra_source_type"
        const val SOURCE_CAMERA = "camera"
        const val SOURCE_GALLERY = "gallery"
    }

    private lateinit var binding: ActivityPreviewBinding
    private val imagePreprocessor = ImagePreprocessor()
    private lateinit var ocrProcessor: OCRProcessor
    private var imageFile: File? = null
    private var sourceType: String = SOURCE_CAMERA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ocrProcessor = OCRProcessor(this)
        sourceType = intent.getStringExtra(EXTRA_SOURCE_TYPE) ?: SOURCE_CAMERA
        val path = intent.getStringExtra(EXTRA_IMAGE_PATH)

        if (path.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.err_invalid_image), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, getString(R.string.err_invalid_image), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        imageFile = file

        loadPreviewBitmap(file)
        setupButtons()
    }

    private fun loadPreviewBitmap(file: File) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ImageUtils.decodeAndNormalizeBitmap(file)
            }
            if (bitmap == null) {
                Toast.makeText(
                    this@PreviewActivity,
                    getString(R.string.err_image_decode),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } else {
                binding.ivPreviewImage.setImageBitmap(bitmap)
            }
        }
    }

    private fun setupButtons() {
        binding.btnRetake.setOnClickListener {
            imageFile?.delete()
            if (sourceType == SOURCE_CAMERA) {
                startActivity(Intent(this, CameraActivity::class.java))
            }
            finish()
        }

        binding.btnConfirmOcr.setOnClickListener {
            val file = imageFile ?: return@setOnClickListener
            runOcrPipeline(file)
        }
    }

    private fun getSelectedLanguageCode(): String {
        return when (binding.rgLanguageMode.checkedRadioButtonId) {
            R.id.rbLangEng -> OCRConfig.LANG_ENGLISH
            R.id.rbLangAra -> OCRConfig.LANG_ARABIC
            else -> OCRConfig.LANG_MIXED_ARA_ENG
        }
    }

    private fun runOcrPipeline(file: File) {
        val selectedLang = getSelectedLanguageCode()
        val applyEnhancement = binding.cbEnablePreprocessing.isChecked

        binding.loadingOverlay.visibility = View.VISIBLE
        binding.btnConfirmOcr.isEnabled = false
        binding.btnRetake.isEnabled = false

        lifecycleScope.launch {
            val processedBitmap: Bitmap? = withContext(Dispatchers.Default) {
                imagePreprocessor.preprocess(file, applyEnhancement)
            }

            if (processedBitmap == null) {
                binding.loadingOverlay.visibility = View.GONE
                binding.btnConfirmOcr.isEnabled = true
                binding.btnRetake.isEnabled = true
                Toast.makeText(
                    this@PreviewActivity,
                    getString(R.string.err_image_decode),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val ocrResult = ocrProcessor.recognizeText(processedBitmap, selectedLang)
            binding.loadingOverlay.visibility = View.GONE
            binding.btnConfirmOcr.isEnabled = true
            binding.btnRetake.isEnabled = true

            when (ocrResult) {
                is OCRResult.Success -> {
                    val doc = ScannedDocument(
                        imageUri = Uri.fromFile(file),
                        extractedText = ocrResult.extractedText,
                        languageCode = ocrResult.languageCode,
                        confidenceScore = ocrResult.meanConfidence
                    )
                    val resultIntent = Intent(this@PreviewActivity, ResultActivity::class.java).apply {
                        putExtra(ResultActivity.EXTRA_EXTRACTED_TEXT, doc.extractedText)
                        putExtra(ResultActivity.EXTRA_CONFIDENCE, doc.confidenceScore)
                        putExtra(ResultActivity.EXTRA_LANGUAGE, doc.languageCode)
                    }
                    startActivity(resultIntent)
                    finish()
                }
                is OCRResult.Error -> {
                    val message = when (ocrResult.errorType) {
                        OCRResult.ErrorType.MISSING_TESSDATA ->
                            getString(R.string.err_missing_tessdata, ocrResult.details ?: "eng/ara")
                        OCRResult.ErrorType.UNSUPPORTED_LANGUAGE ->
                            getString(R.string.err_unsupported_language, ocrResult.details ?: "")
                        OCRResult.ErrorType.INIT_FAILED ->
                            getString(R.string.err_ocr_init)
                        OCRResult.ErrorType.EMPTY_RESULT ->
                            getString(R.string.err_empty_ocr_result)
                        OCRResult.ErrorType.INSUFFICIENT_STORAGE ->
                            getString(R.string.err_insufficient_storage)
                        OCRResult.ErrorType.PROCESSING_FAILURE ->
                            getString(R.string.err_ocr_failed, ocrResult.details ?: "")
                    }
                    Toast.makeText(this@PreviewActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

```

### `app/src/main/java/com/example/offlinetextscanner/imageprocessing/ImageUtils.kt`

```
package com.example.offlinetextscanner.imageprocessing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Provides safe bitmap decoding, resizing, and EXIF rotation/orientation correction
 * while preserving the original file on disk unchanged.
 */
object ImageUtils {

    private const val MAX_OCR_DIMENSION = 2200

    fun decodeAndNormalizeBitmap(imageFile: File): Bitmap? {
        if (!imageFile.exists() || imageFile.length() == 0L) return null

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(
            boundsOptions.outWidth,
            boundsOptions.outHeight,
            MAX_OCR_DIMENSION,
            MAX_OCR_DIMENSION
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions) ?: return null
        val rotatedBitmap = applyExifOrientation(rawBitmap, imageFile.absolutePath)
        return resizeIfNeeded(rotatedBitmap, MAX_OCR_DIMENSION)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun applyExifOrientation(bitmap: Bitmap, filePath: String): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotationDegrees == 0f) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                rotated
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    fun resizeIfNeeded(bitmap: Bitmap, maxDimension: Int = MAX_OCR_DIMENSION): Bitmap {
        val currentMax = max(bitmap.width, bitmap.height)
        if (currentMax <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / currentMax.toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        if (resized != bitmap) bitmap.recycle()
        return resized
    }
}

```

### `app/src/main/java/com/example/offlinetextscanner/imageprocessing/ImageEnhancer.kt`

```
package com.example.offlinetextscanner.imageprocessing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Uses OpenCV to enhance document images for OCR:
 * 1. Grayscale conversion
 * 2. CLAHE local contrast enhancement
 * 3. Mild Gaussian noise reduction
 * 4. Adaptive Gaussian thresholding (preserving Arabic diacritics and fine strokes)
 */
object ImageEnhancer {

    fun enhanceBitmapForOcr(sourceBitmap: Bitmap): Bitmap {
        val srcMat = Mat()
        val grayMat = Mat()
        val contrastMat = Mat()
        val denoisedMat = Mat()
        val thresholdMat = Mat()

        try {
            Utils.bitmapToMat(sourceBitmap, srcMat)

            // 1. Grayscale conversion
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            // 2. Contrast enhancement via CLAHE
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(grayMat, contrastMat)

            // 3. Mild Gaussian blur for high-frequency sensor noise reduction
            Imgproc.GaussianBlur(contrastMat, denoisedMat, Size(3.0, 3.0), 0.0)

            // 4. Adaptive Gaussian thresholding for uneven illumination
            Imgproc.adaptiveThreshold(
                denoisedMat,
                thresholdMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                31,
                12.0
            )

            val outputBitmap = Bitmap.createBitmap(
                sourceBitmap.width,
                sourceBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(thresholdMat, outputBitmap)
            return outputBitmap
        } finally {
            srcMat.release()
            grayMat.release()
            contrastMat.release()
            denoisedMat.release()
            thresholdMat.release()
        }
    }
}

```

### `app/src/main/java/com/example/offlinetextscanner/imageprocessing/ImagePreprocessor.kt`

```
package com.example.offlinetextscanner.imageprocessing

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * Coordinates the local image preprocessing pipeline before Tesseract OCR.
 * Ensures the original image file on disk remains unmodified.
 */
class ImagePreprocessor {

    @Volatile
    private var isOpenCvInitialized = false

    @Synchronized
    fun ensureOpenCvLoaded(): Boolean {
        if (!isOpenCvInitialized) {
            isOpenCvInitialized = OpenCVLoader.initLocal()
        }
        return isOpenCvInitialized
    }

    /**
     * Decodes, normalizes orientation/size, and optionally runs the OpenCV enhancement pipeline.
     * The original image file is never modified.
     */
    fun preprocess(imageFile: File, applyOpenCvEnhancement: Boolean = true): Bitmap? {
        val normalizedBitmap = ImageUtils.decodeAndNormalizeBitmap(imageFile) ?: return null
        if (!applyOpenCvEnhancement) {
            return normalizedBitmap
        }

        return if (ensureOpenCvLoaded()) {
            try {
                ImageEnhancer.enhanceBitmapForOcr(normalizedBitmap)
            } catch (e: Exception) {
                // Fallback gracefully to the normalized bitmap if OpenCV processing fails
                normalizedBitmap
            }
        } else {
            normalizedBitmap
        }
    }
}

```

### `app/src/main/java/com/example/offlinetextscanner/ocr/OCRConfig.kt`

```
package com.example.offlinetextscanner.ocr

import com.googlecode.tesseract.android.TessBaseAPI

/**
 * Defines supported offline OCR languages and Tesseract configuration constants.
 */
object OCRConfig {
    const val LANG_ENGLISH = "eng"
    const val LANG_ARABIC = "ara"
    const val LANG_MIXED_ARA_ENG = "ara+eng"

    val SUPPORTED_LANGUAGE_MODES = setOf(
        LANG_ENGLISH,
        LANG_ARABIC,
        LANG_MIXED_ARA_ENG
    )

    val REQUIRED_TRAINEDDATA_FILES = listOf(
        "eng.traineddata",
        "ara.traineddata"
    )

    const val DEFAULT_OCR_ENGINE_MODE = TessBaseAPI.OEM_LSTM_ONLY
    const val DEFAULT_PAGE_SEG_MODE = TessBaseAPI.PageSegMode.PSM_AUTO

    fun isSupportedLanguage(languageCode: String): Boolean {
        return SUPPORTED_LANGUAGE_MODES.contains(languageCode)
    }
}

```

### `app/src/main/java/com/example/offlinetextscanner/ocr/OCRResult.kt`

```
package com.example.offlinetextscanner.ocr

/**
 * Encapsulates the outcome of a local Tesseract OCR operation.
 */
sealed class OCRResult {
    data class Success(
        val extractedText: String,
        val meanConfidence: Int,
        val languageCode: String
    ) : OCRResult()

    data class Error(
        val errorType: ErrorType,
        val details: String? = null
    ) : OCRResult()

    enum class ErrorType {
        MISSING_TESSDATA,
        UNSUPPORTED_LANGUAGE,
        INIT_FAILED,
        EMPTY_RESULT,
        INSUFFICIENT_STORAGE,
        PROCESSING_FAILURE
    }
}

```

### `app/src/main/java/com/example/offlinetextscanner/ocr/OCRProcessor.kt`

```
package com.example.offlinetextscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import com.example.offlinetextscanner.utils.FileHelper
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Initializes Tesseract locally from internal storage and executes OCR on a background thread.
 */
class OCRProcessor(private val context: Context) {

    suspend fun recognizeText(
        bitmap: Bitmap,
        languageCode: String = OCRConfig.LANG_MIXED_ARA_ENG
    ): OCRResult = withContext(Dispatchers.Default) {
        if (!OCRConfig.isSupportedLanguage(languageCode)) {
            return@withContext OCRResult.Error(
                OCRResult.ErrorType.UNSUPPORTED_LANGUAGE,
                languageCode
            )
        }

        val dataPath: String = try {
            FileHelper.prepareTessDataDirectory(context)
        } catch (e: FileHelper.MissingTessDataException) {
            return@withContext OCRResult.Error(
                OCRResult.ErrorType.MISSING_TESSDATA,
                e.missingFiles.joinToString(", ")
            )
        } catch (e: Exception) {
            return@withContext OCRResult.Error(
                OCRResult.ErrorType.INSUFFICIENT_STORAGE,
                e.message
            )
        }

        val tessBaseAPI = TessBaseAPI()
        try {
            val initialized = tessBaseAPI.init(
                dataPath,
                languageCode,
                OCRConfig.DEFAULT_OCR_ENGINE_MODE
            )
            if (!initialized) {
                return@withContext OCRResult.Error(OCRResult.ErrorType.INIT_FAILED)
            }

            tessBaseAPI.pageSegMode = OCRConfig.DEFAULT_PAGE_SEG_MODE
            tessBaseAPI.setImage(bitmap)

            val rawText = tessBaseAPI.utF8Text?.trim().orEmpty()
            val confidence = tessBaseAPI.meanConfidence()

            if (rawText.isEmpty()) {
                OCRResult.Error(OCRResult.ErrorType.EMPTY_RESULT)
            } else {
                OCRResult.Success(
                    extractedText = rawText,
                    meanConfidence = confidence,
                    languageCode = languageCode
                )
            }
        } catch (e: Exception) {
            OCRResult.Error(
                OCRResult.ErrorType.PROCESSING_FAILURE,
                e.localizedMessage ?: "Unknown Tesseract error"
            )
        } finally {
            try {
                tessBaseAPI.recycle()
            } catch (_: Exception) {
            }
        }
    }
}

```

### `app/src/main/res/layout/activity_result.xml`

```
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_white"
    android:padding="@dimen/spacing_md">

    <TextView
        android:id="@+id/tvResultTitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:text="@string/result_title"
        android:textColor="@color/text_primary"
        android:textSize="20sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/tvConfidenceMeta"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/spacing_xs"
        android:textColor="@color/text_secondary"
        android:textSize="13sp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/tvResultTitle" />

    <EditText
        android:id="@+id/etExtractedText"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginTop="@dimen/spacing_md"
        android:layout_marginBottom="@dimen/spacing_md"
        android:background="@color/surface_gray"
        android:gravity="top|start"
        android:hint="@string/result_hint"
        android:importantForAutofill="no"
        android:inputType="textMultiLine|textCapSentences"
        android:lineSpacingExtra="5dp"
        android:padding="@dimen/spacing_md"
        android:scrollbars="vertical"
        android:textAlignment="viewStart"
        android:textColor="@color/text_primary"
        android:textDirection="anyRtl"
        android:textIsSelectable="true"
        android:textSize="16sp"
        app:layout_constraintBottom_toTopOf="@id/resultActionRow"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/tvConfidenceMeta" />

    <LinearLayout
        android:id="@+id/resultActionRow"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginBottom="@dimen/spacing_sm"
        android:orientation="horizontal"
        app:layout_constraintBottom_toTopOf="@id/btnScanAnother"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnCopyAll"
            android:layout_width="0dp"
            android:layout_height="@dimen/button_height"
            android:layout_marginEnd="@dimen/spacing_xs"
            android:layout_weight="1"
            android:text="@string/btn_copy_all"
            android:textAllCaps="false"
            android:textColor="@color/background_white"
            app:backgroundTint="@color/primary_blue"
            app:cornerRadius="@dimen/button_corner_radius" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnShareText"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"
            android:layout_width="0dp"
            android:layout_height="@dimen/button_height"
            android:layout_marginStart="@dimen/spacing_xs"
            android:layout_weight="1"
            android:text="@string/btn_share"
            android:textAllCaps="false"
            android:textColor="@color/primary_blue"
            app:cornerRadius="@dimen/button_corner_radius"
            app:strokeColor="@color/primary_blue" />
    </LinearLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnScanAnother"
        style="@style/Widget.MaterialComponents.Button.TextButton"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:text="@string/btn_scan_another"
        android:textAllCaps="false"
        android:textColor="@color/primary_blue"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>

```

### `app/src/main/java/com/example/offlinetextscanner/activities/ResultActivity.kt`

```
package com.example.offlinetextscanner.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.offlinetextscanner.MainActivity
import com.example.offlinetextscanner.R
import com.example.offlinetextscanner.databinding.ActivityResultBinding
import com.example.offlinetextscanner.utils.ClipboardHelper
import com.example.offlinetextscanner.utils.ShareHelper

/**
 * Displays extracted OCR text with full support for Arabic RTL and English LTR editing,
 * selection, clipboard copying, and native Android sharing.
 */
class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXTRACTED_TEXT = "extra_extracted_text"
        const val EXTRA_CONFIDENCE = "extra_confidence"
        const val EXTRA_LANGUAGE = "extra_language"
    }

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extractedText = intent.getStringExtra(EXTRA_EXTRACTED_TEXT).orEmpty()
        val confidence = intent.getIntExtra(EXTRA_CONFIDENCE, 0)
        val language = intent.getStringExtra(EXTRA_LANGUAGE).orEmpty()

        binding.etExtractedText.setText(extractedText)
        binding.tvConfidenceMeta.text = "Language: $language • Mean Confidence: $confidence%"

        binding.btnCopyAll.setOnClickListener {
            val currentText = binding.etExtractedText.text?.toString().orEmpty()
            val copied = ClipboardHelper.copyToClipboard(this, currentText)
            if (copied) {
                Toast.makeText(
                    this,
                    getString(R.string.text_copied_toast),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.err_clipboard_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnShareText.setOnClickListener {
            val currentText = binding.etExtractedText.text?.toString().orEmpty()
            ShareHelper.shareText(this, currentText)
        }

        binding.btnScanAnother.setOnClickListener {
            val homeIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(homeIntent)
            finish()
        }
    }
}

```

## 5. Phase 8: Manual `tessdata` Setup, Build Commands & Offline Test Checklist

### Step 1: Download Local Tesseract LSTM Language Data Files

Download `eng.traineddata` and `ara.traineddata` from the official [tesseract-ocr/tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast) repository and place them inside `app/src/main/assets/tessdata/` before compiling:

```
mkdir -p OfflineTextScanner/app/src/main/assets/tessdata
curl -L https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata \
  -o OfflineTextScanner/app/src/main/assets/tessdata/eng.traineddata
curl -L https://github.com/tesseract-ocr/tessdata_fast/raw/main/ara.traineddata \
  -o OfflineTextScanner/app/src/main/assets/tessdata/ara.traineddata

```

### Step 2: Build the Debug APK from Visual Studio Code Terminal

Ensure `ANDROID_HOME` points to your local Android SDK installation (with Android SDK Platform 34 and Build-Tools 34.0.0 installed), then run:

```
cd OfflineTextScanner
./gradlew clean assembleDebug

```

The compiled APK will be generated at:
`OfflineTextScanner/app/build/outputs/apk/debug/app-debug.apk`

To install and test on a connected Android device with Wi-Fi and mobile data turned off:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk

```

### Step 3: Verification & Error-Handling Matrix

* **English, Arabic, and Mixed (`ara+eng`) Documents**: Select the corresponding radio button on `PreviewActivity`; `ResultActivity` automatically configures `android:textDirection="anyRtl"` and `android:textAlignment="viewStart"` so Arabic paragraphs align RTL and English paragraphs align LTR.

* **Offline Execution**: `AndroidManifest.xml` omits `android.permission.INTERNET`, `OpenCVLoader.initLocal()` loads bundled native binaries, and `FileHelper.prepareTessDataDirectory()` copies `eng.traineddata` and `ara.traineddata` from APK assets into `<filesDir>/tesseract/tessdata/`.

* **Missing `tessdata` / Blurry / Invalid Images**: Handled via typed `OCRResult.Error` states (`MISSING_TESSDATA`, `EMPTY_RESULT`, `INIT_FAILED`, `INSUFFICIENT_STORAGE`) with clear user-facing Toast notifications.