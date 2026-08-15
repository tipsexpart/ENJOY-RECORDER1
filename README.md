# ENJOY RECORDER - Android Native Project

This repository contains the complete native Android project for **ENJOY RECORDER** (Kotlin 2.0, Jetpack Compose, MediaProjection Foreground Service, OpenGL ES 2.0 Region Crop, and Google Mobile Ads Test Integration).

---

## 5-Step Guide to Build & Install Debug APK

### 1. Download the Project ZIP
Download and unzip this project onto your computer.

### 2. Upload to a GitHub Repository
1. Create a new GitHub repository (Public or Private) at https://github.com/new.
2. Push or upload these unzipped project files directly to the root of your GitHub repository.

### 3. Run GitHub Actions Workflow
1. In your GitHub repository, click on the **Actions** tab.
2. Select the **"Build Android Debug APK"** workflow.
3. Click **"Run workflow"** (or simply push any commit to trigger it automatically).

### 4. Download Generated APK Artifact
1. Once the workflow completes (~2 minutes), click on the completed workflow run.
2. Scroll down to the **Artifacts** section and click on **`enjoy-recorder-debug-apk`** to download the zip containing `app-debug.apk`.

### 5. Install APK on Android Phone
1. Transfer `app-debug.apk` to your Android phone via USB, Google Drive, or email.
2. Enable *"Install unknown apps"* for your file manager if prompted.
3. Tap **Install** and open **ENJOY RECORDER**!

---

## Local Build (Without GitHub):
```bash
# macOS / Linux:
chmod +x gradlew
./gradlew assembleDebug

# Windows (CMD / PowerShell):
gradlew.bat assembleDebug
```
Generated APK: `app/build/outputs/apk/debug/app-debug.apk`
