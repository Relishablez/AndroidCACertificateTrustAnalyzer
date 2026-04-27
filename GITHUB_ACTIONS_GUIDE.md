# GitHub Actions CI/CD Guide

This guide explains how to use GitHub Actions to automatically build your Android APK without installing Android Studio locally.

---

## What is GitHub Actions?

GitHub Actions is a **free CI/CD service** (Continuous Integration/Continuous Deployment) that runs build tasks in the cloud. Every time you push code to GitHub, it automatically:
1. Downloads your code
2. Sets up the Android SDK
3. Builds your APK
4. Saves the APK file for you to download

**Cost**: Free for public repositories (unlimited minutes). Private repositories get 2,000 minutes/month free.

---

## Setup Instructions

### Step 1: Create a GitHub Repository

1. Go to https://github.com/new
2. Repository name: `AndroidCACertificateTrustAnalyzer`
3. Make it **Public** (for free unlimited builds) or **Private** (2,000 min/month free)
4. **DO NOT** initialize with README (you already have one)
5. Click **Create repository**

### Step 2: Push Your Code to GitHub

Open a terminal in your project folder and run:

```bash
# Initialize git (if not already done)
git init

# Add all files
git add .

# Commit
git commit -m "Initial commit - CA Certificate Analyzer"

# Connect to GitHub (replace YOUR_USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR_USERNAME/AndroidCACertificateTrustAnalyzer.git

# Push to GitHub
git branch -M main
git push -u origin main
```

Or if you prefer PowerShell on Windows:
```powershell
git init
git add .
git commit -m "Initial commit - CA Certificate Analyzer"
git remote add origin https://github.com/YOUR_USERNAME/AndroidCACertificateTrustAnalyzer.git
git branch -M main
git push -u origin main
```

### Step 3: Verify GitHub Actions is Working

1. Go to your GitHub repository: `https://github.com/YOUR_USERNAME/AndroidCACertificateTrustAnalyzer`
2. Click the **Actions** tab at the top
3. You should see a workflow called "Build APK" running or completed
4. Wait 3-5 minutes for the build to complete

---

## Downloading Your APK

### Method 1: Download from GitHub Actions (Recommended)

1. Go to **Actions** tab in your GitHub repository
2. Click on the latest successful workflow run (green checkmark)
3. Scroll down to **Artifacts** section
4. Download:
   - `debug-apk` → Contains `app-debug.apk` (for testing)
   - `release-apk` → Contains `app-release-unsigned.apk` (for release)

![Artifacts Section](https://docs.github.com/assets/images/help/repository/artifact-drop-down.png)

### Method 2: Create a GitHub Release (For Distribution)

To automatically attach APKs to a GitHub Release:

1. Create a version tag:
   ```bash
   git tag -a v1.0.0 -m "Version 1.0.0"
   git push origin v1.0.0
   ```

2. Go to **Releases** section on GitHub
3. The APK files will be automatically attached to the release

---

## Understanding the Workflow File

The workflow file (`.github/workflows/build-apk.yml`) has these key parts:

### Triggers (When it runs)
```yaml
on:
  push:
    branches: [ main, master ]    # Runs on every push to main
  pull_request:
    branches: [ main, master ]  # Runs on pull requests
  workflow_dispatch:             # Allows manual trigger
```

### Jobs (What it does)
```yaml
jobs:
  build:
    runs-on: ubuntu-latest      # Uses Ubuntu Linux (free)
```

### Steps (How it does it)
1. **Checkout** - Downloads your code
2. **Setup Java** - Installs JDK 17
3. **Build** - Compiles the APK
4. **Upload** - Saves APK as downloadable artifact

---

## Troubleshooting

### Build Failed - "Gradle Wrapper Not Found"

**Error**: `Could not find or load main class org.gradle.wrapper.GradleWrapperMain`

**Solution**: You need the `gradle-wrapper.jar` binary file. Download it:

```bash
# Run this in your project folder
cd 107_AndroidCACertificateTrustAnalyzer

# Download the wrapper JAR
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar

# Or on PowerShell:
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar" `
  -OutFile "gradle/wrapper/gradle-wrapper.jar"

# Commit and push
git add gradle/wrapper/gradle-wrapper.jar
git commit -m "Add Gradle wrapper JAR"
git push
```

### Build Failed - "Could not resolve dependencies"

**Solution**: Check your internet connection and try re-running the workflow:
1. Go to **Actions** tab
2. Click on the failed workflow
3. Click **Re-run jobs** (top right)

### Build Warnings

Some warnings are normal and can be ignored:
- "This version only understands SDK XML versions up to 2"
- AGP (Android Gradle Plugin) compatibility warnings

---

## Customizing the Build

### Change Android SDK Version

Edit `app/build.gradle.kts`:
```kotlin
android {
    compileSdk = 35  // Change to 35 for latest
    defaultConfig {
        minSdk = 26  // Minimum Android 8.0
        targetSdk = 34
    }
}
```

### Add Signed Release Builds (For Play Store)

To create a signed APK for Google Play Store, you need to add signing configuration:

1. Create a keystore (do this once locally):
```bash
keytool -genkey -v -keystore release.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias cacertanalyzer
```

2. Add GitHub Secrets:
   - Go to GitHub → Settings → Secrets and variables → Actions
   - Add `KEYSTORE_BASE64` (base64 encoded keystore)
   - Add `KEYSTORE_PASSWORD`
   - Add `KEY_ALIAS`
   - Add `KEY_PASSWORD`

3. Update the workflow file with signing steps (let me know if you need this)

---

## Workflow Status Badges

Add this to your README.md to show build status:

```markdown
![Build APK](https://github.com/YOUR_USERNAME/AndroidCACertificateTrustAnalyzer/workflows/Build%20APK/badge.svg)
```

---

## Next Steps After Downloading APK

1. **Install on Android**:
   - Transfer APK to your device
   - Enable "Install from Unknown Sources" in Settings → Security
   - Tap the APK file to install

2. **Enable Developer Mode** (if using ADB):
   - Settings → About Phone → Tap "Build Number" 7 times
   - Settings → Developer Options → Enable USB Debugging

---

## Helpful Commands

### Check Workflow Status Locally
```bash
# View recent GitHub Actions runs
gh run list

# View logs of latest run
gh run view --log

# Download artifacts via CLI
gh run download <run-id> --name debug-apk
```

### Re-run Failed Build
```bash
gh run rerun <run-id>
```

---

## Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Android CI/CD Guide](https://developer.android.com/studio/projects/continuous-integration)
- [Gradle Wrapper Documentation](https://docs.gradle.org/current/userguide/gradle_wrapper.html)

---

## Need Help?

If the GitHub Actions build fails:
1. Click on the failed workflow run
2. Expand the failed step to see the error
3. Common issues:
   - Missing `gradle-wrapper.jar` (see Troubleshooting above)
   - Syntax errors in Kotlin files
   - Missing Android SDK components (workflow handles this)

Feel free to check the **information.md** file for code-specific help.
