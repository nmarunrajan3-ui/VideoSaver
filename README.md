# InstaSaver – Standalone Android Application

InstaSaver is a 100% standalone, personal-use Android application built with modern Android architecture. It allows you to download and archive Instagram media (Reels, Video posts, Photos, Carousels, and authorized Stories) directly to your device storage.

---

## Key Highlights

- **Zero Website & Zero Backend**: 100% standalone Android APK operating completely offline/locally on your smartphone.
- **Privacy First**: No account registration, no subscriptions, no ads, no trackers, and **no Instagram password collection**.
- **Modern Jetpack Compose & Material 3**: Beautiful, minimalist interface with dark mode as default, light mode, and system theme options.
- **Modern Android Scoped Storage**:
  - Images saved to: `Pictures/InstaSaver`
  - Videos saved to: `Movies/InstaSaver`
  - Automatically indexed so media shows directly in Google Photos and your system Gallery app.
- **Background Downloads with WorkManager**: Reliable downloads with progress indicators, cancel/retry, and duplicate detection.
- **In-App Media Player**: Built-in Jetpack Media3 ExoPlayer for instant video playback and Coil for photo/carousel inspection.
- **Android Share-Sheet Integration**: Tap "Share" in the official Instagram app or browser and choose InstaSaver to download directly.
- **Authorized Instagram Access Only**: Respects privacy, copyrights, and Instagram access controls without circumventing private account protections.

---

## Architecture & Technology Stack

| Component | Technology | Description |
|---|---|---|
| **Language** | Kotlin 1.9.22 | Coroutines & Flow for asynchronous reactive state |
| **UI** | Jetpack Compose + Material 3 | Declarative modern UI with dark mode default |
| **Local Database** | Room 2.6.1 | Local SQLite persistence for download history |
| **Preferences** | Jetpack DataStore | Theme selection & user settings |
| **Background Work** | WorkManager 2.9.0 | Foreground/background downloads with progress |
| **Storage Engine** | Android MediaStore API | Scoped storage compliant (`Pictures/` & `Movies/`) |
| **Networking** | OkHttp 4.12.0 | Secure HTTPS client |
| **Video Player** | Jetpack Media3 ExoPlayer 1.3.0 | Smooth in-app playback |
| **Image Loading** | Coil Compose 2.6.0 | Image and thumbnail rendering |
| **Compatibility** | Min SDK 26 (Android 8.0) | Target SDK 34 (Android 14) |

---

## Application Sections & Screens

1. **Home Screen**:
   - URL input field with Paste clipboard button, Clear button, and Download trigger.
   - Quick statistics banner (completed downloads, total MB saved).
   - Category cards: Reels, Video Posts, Photos & Carousels, Stories.
   - Manual Media Import action (import & organize existing media from your phone).
   - Recent downloads list with direct play, share, and delete.
2. **Reels Section**:
   - Filtered view of downloaded Instagram Reels with in-app video playback.
3. **Videos Section**:
   - Filtered view of downloaded video posts and IGTV content.
4. **Photos Section**:
   - Filtered view of downloaded photos and carousel albums with full-screen zoomable preview.
5. **Stories Section**:
   - Dedicated interface explaining authorized access limitations and manual import/archival for stories you own.
6. **Downloads Screen**:
   - Filter tabs: All, Active, Completed, Failed.
   - Real-time download progress indicators, cancel active downloads, retry failed downloads, clear history.
7. **Download Details Screen**:
   - Preview, shortcode, category, resolution, file size, storage location, and share actions.
8. **Settings Screen**:
   - Theme toggle: Dark (default), Light, System Default.
   - Duplicate download warning toggle.
   - Completion notifications toggle.
   - Storage directory breakdown and database cleanup.
9. **About Screen**:
   - Version details, technology overview, privacy statement, and legal disclaimer.

---

## How to Build the Project

### Prerequisites
- JDK 17
- Android SDK with API 34 platforms and build-tools installed
- `ANDROID_HOME` or `local.properties` pointing to your Android SDK

### Build Commands

To build the debug APK:
```bash
./gradlew assembleDebug
```
The installable APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

To run unit tests:
```bash
./gradlew test
```

To clean the build:
```bash
./gradlew clean
```

---

## How to Install the APK on Your Phone

1. Enable **Install unknown apps** on your Android device (Settings → Apps → Special app access → Install unknown apps).
2. Transfer `app-debug.apk` to your phone via USB cable, Nearby Share, or direct download.
3. Open the file on your device and tap **Install**.
4. Launch **InstaSaver** and start saving your media!

---

## Privacy & Security Policy

- **No Instagram Password Collection**: InstaSaver will never ask for your Instagram account password.
- **Zero Cloud / Analytics**: No user analytics, no Firebase, no crash telemetry, and no third-party tracking scripts.
- **Respect for Access Controls**: If content is private or restricted by Instagram access controls, InstaSaver displays a clear explanation rather than attempting unauthorized bypass.
