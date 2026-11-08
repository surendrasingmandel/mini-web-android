# Mini Web
**Fast. Private. Fully Yours.**

A native Android browser starter project built with Kotlin, Jetpack Compose, Material 3 and Android WebView.

## Requirements
- Android Studio Ladybug or newer (recommended)
- JDK 17
- Android SDK Platform 35 (compile/target)
- Minimum supported Android version: Android 12 / API 31

## Open and run
1. Extract `Mini-Web-Android-Studio.zip`.
2. Open the `mini-web` folder in Android Studio.
3. Allow Gradle sync and install the requested Android SDK components.
4. Connect an Android 12+ device or start an emulator.
5. Select the `app` run configuration and press Run.

The first clean install uses DuckDuckGo as its default search engine. Settings are stored locally with DataStore. Bookmarks and history use Room.

## Implemented in this baseline
- Native Compose browser interface with address/search bar.
- DuckDuckGo default search and selectable search engines.
- WebView navigation, back/forward, reload and secure HTTPS error handling.
- Multiple tab records, tab switching and closing.
- Room-backed bookmarks and browsing history.
- DownloadManager integration for web downloads.
- Theme choices: System, Light, Dark and AMOLED.
- Persistent privacy preferences for ads, trackers, third-party cookies and history.
- Local privacy dashboard with session request-block count.
- Basic host-based ad/tracker request blocking.
- Desktop site preference storage.

## Important scope and security notes
This is a functional first development baseline, not a claim that every item in the full product brief is complete. The built-in blocker currently uses a small host denylist. It is not a full EasyList/EasyPrivacy parser, does not yet download signed filter updates, and does not provide comprehensive cosmetic filtering. The displayed blocked count is session-scoped. Tab metadata is managed in Compose, but independent WebView renderer state per tab and full session persistence are not yet implemented. Private tabs are represented in the tab model but a complete isolated private data profile is not yet implemented. HTTPS-only navigation, DNS-over-HTTPS, phishing reputation checks, biometric app lock, QR scanning, reader mode, translation, offline pages, cloud sync, import/export and advanced website permission UI remain planned work.

WebView is not Chromium's full Chrome engine. Some browser features cannot be made identical to Chrome using Android WebView. JavaScript is enabled for normal modern websites; file/content access is disabled, mixed content is blocked, unsafe URL schemes are not loaded in WebView, and SSL certificate errors are cancelled. Do not treat this browser as anonymous or as a substitute for a security-hardened browser engine.

## Build variants
- `debug`: installable development build, application ID suffix `.debug`.
- `release`: minified release configuration. Configure a private signing key in Android Studio before distribution. Never commit signing credentials.

## Project structure
- `data/`: Room database and DataStore preferences.
- `engine/`: request filtering engine.
- `ui/`: Compose browser, tabs, settings and privacy screens.
- `ui/theme/`: Material 3 theme configuration.

## Privacy
Browsing history, bookmarks and preferences are stored on-device. Search requests go directly to the selected search provider. No Mini Web analytics endpoint or custom telemetry is configured in this project.

## Build an APK using your Android phone (GitHub Actions)

1. Create a GitHub repository.
2. Upload `Mini-Web-Android-Studio.zip` to the repository root.
3. In GitHub, choose **Add file → Create new file**.
4. Enter `.github/workflows/build.yml` as the filename.
5. Copy the workflow from `.github/workflows/build.yml` in this project (or use the separate `build.yml` supplied with the download) and commit it.
6. Open the repository's **Actions** tab, choose **Build Mini Web APK**, and tap **Run workflow**.
7. Wait for the workflow to finish. Open the successful run and download the `mini-web-debug-apk` artifact.
8. Extract the downloaded artifact ZIP on your phone and install the APK. If Android asks, allow your browser or file manager to install unknown apps.

The workflow builds with JDK 17 and Gradle 8.9, installs Android SDK 35, runs unit tests, and uploads the debug APK. The app's `minSdk` is 31 (Android 12).

For easiest setup, keep the repository public. Never upload signing keys, passwords, API secrets, or private credentials to the repository.

## Homepage and navigation update

The current development build includes a Mini Web start page, persistent custom wallpaper selection, quick links, Downloads screen backed by Android DownloadManager, and corrected Android back handling (web history first, then Mini Web home, then exit confirmation).

Private browsing and background/lock-screen media playback are not yet implemented in this update. The private-mode homepage action intentionally displays an explanation instead of pretending that normal WebView cookies/storage are isolated. Background media requires a dedicated media-session integration and site compatibility testing; it is not claimed as complete here.

## Phase 2 privacy and media additions

- The homepage wallpaper picker uses Android's document picker and persists the selected image URI in DataStore.
- Private browsing opens `IncognitoActivity` in a dedicated Android process. That process sets the WebView data-directory suffix to `miniweb_incognito`, isolating its cookies, DOM storage, and WebView data from the normal browser profile. The private WebView clears cookies, web storage, cache, and history when the private window closes.
- The normal browser monitors HTML5 `<audio>` and `<video>` elements in the current document. When media starts, `WebPlaybackService` enters Android media-playback foreground mode and publishes a MediaSession-backed notification and lock-screen controls. The notification updates its title and site name, and provides Play/Pause and Stop controls. Transport commands are sent to media elements accessible in the current page document and same-origin frames. Some websites, including services that restrict background playback or place media inside inaccessible cross-origin frames, may not support this behavior. This does not bypass YouTube Premium or other provider-specific background-playback restrictions.
- Android 13+ notification permission is requested so playback controls can be shown in the notification shade. Lock-screen media controls depend on Android lock-screen notification settings and device policy.

Build with the repository's GitHub Actions workflow. This source update has not yet been verified by a fresh CI build; run the workflow before installing the APK.
