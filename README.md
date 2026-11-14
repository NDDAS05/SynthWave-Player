# SynthWave

A native Android music player built with **Kotlin**, **Jetpack Compose**, and **Media3**, combining automatic device audio indexing, real-time 60fps RGB audio visualization, synchronized lyrics, persistent local playlists, and YouTube Music streaming inside a dark glassmorphic interface.

---

## Features

### 1. Automatic Local Library & Playlists
- **Zero-Config MediaStore Scanning**: Automatically indexes internal storage and SD card audio via `MediaStore.Audio.Media`, filtering out short notification sounds and ringtones under **15 seconds**.
- **Smart Organization**: Browse by **Songs**, **Playlists**, **Folders**, **Artists**, and **Albums**, plus quick filters for **Favorites** and **Recently Played**.
- **Embedded Album Art**: Lazily extracts and caches embedded cover artwork via `MediaMetadataRetriever`, falling back to custom SynthWave artwork when unavailable.
- **Persistent Storage (Room + DataStore)**: Custom playlists, track ordering, Favorites, Recently Played history, custom lyrics, and playback preferences survive app restarts.

### 2. Real-Time 60fps RGB Audio Visualizers
- **Live FFT + Procedural Beat Fallback**: Attaches Android's native `Visualizer` API (`captureSize = 1024`) to ExoPlayer's active `audioSessionId` for real-time bass, mid, and treble frequency analysis. Automatically engages a 60fps **BPM-driven procedural beat-simulation engine** when `RECORD_AUDIO` is declined or when streaming YouTube audio.
- **Dynamic Ambient Background**: A slowly shifting dark RGB gradient mesh (`Obsidian`, `Midnight Violet`, `Dark Crimson`, `Deep Navy`) that pulses subtly with bass kicks.
- **Top-Center Mode Switcher**:
  - **RGB Ring**: Multi-layered glowing circular neon ring (`SweepGradient` + `BlurMaskFilter` bloom) that rotates, twists with mid frequencies, expands on bass kicks, and projects crystalline treble edge ripples.
  - **Cloth Wave**: Starts as a crisp single horizontal neon line during quiet passages and smoothly unfurls into a 3D-perspective silk/cloth mesh of interconnected luminous RGB strands as amplitude builds.

### 3. Synchronized Lyrics & Transcripts
- **Automatic Discovery**: Checks the playing audio file's directory for matching `.lrc` or `.txt` files and parses embedded **ID3v2 `USLT`** lyrics tags.
- **Interactive Sync**: Auto-scrolls and highlights active `[mm:ss.xx]` LRC lines in real time; tap any timestamped line to seek immediately.
- **Import & Edit**: Import external `.lrc`/`.txt` files via the system document picker or paste/edit custom lyrics directly in-app (persisted in Room).

### 4. YouTube Music Streaming & Search
- **Immediate Demo Mode + Live Search**: Includes playable royalty-free demo streams out of the box alongside **YouTube Data API v3** search (configure your API key in the in-app Settings dialog).
- **Audio-Focused with Expandable Video**: Streams via `android-youtube-player` synchronized with the persistent mini-player, keeping the RGB visualizer active by default with an **Expand Video Preview** toggle on the Now Playing screen.

### 5. Background Playback & Edge-to-Edge UI
- **Media3 `MediaSessionService`**: Continues playback smoothly with the screen off, complete with lock-screen media controls and notification bar playback buttons.
- **Glassmorphic Dark UI**: Persistent mini-player above the bottom navigation bar expanding smoothly into the full-screen Now Playing visualizer view.

---

## Tech Stack

| Layer | Technologies |
| --- | --- |
| **UI & Styling** | Jetpack Compose, Material 3, Edge-to-Edge `WindowInsets`, Custom Compose `Canvas` (`SweepGradient`, `BlurMaskFilter`), Coil, Bundled Fonts (`Space Grotesk`, `Plus Jakarta Sans`, `JetBrains Mono`) |
| **Audio & Media** | Jetpack Media3 (`ExoPlayer`, `MediaSessionService`, `MediaController`), `android.media.audiofx.Visualizer`, `MediaMetadataRetriever`, `android-youtube-player` |
| **Architecture & State** | MVVM, Kotlin Coroutines & `StateFlow`, Constructor Injection |
| **Persistence** | Room (KSP), Jetpack DataStore Preferences |
| **Networking** | Retrofit 2, OkHttp, Moshi (`KotlinJsonAdapterFactory`) |
| **Testing** | JUnit 4, Robolectric, Coroutines Test |

---

## Architecture Overview

```text
Compose UI (LocalLibraryScreen • YouTubeMusicScreen • NowPlayingScreen • LyricsOverlaySheet)
        │
        ▼
ViewModels (LibraryViewModel • YouTubeViewModel • NowPlayingViewModel)
        │
        ├──▶ PlaybackCoordinator ──▶ Media3 SynthWavePlaybackService (ExoPlayer + MediaSession)
        │                        ├──▶ VisualizerEngine (Android Visualizer FFT / BPM Synth)
        │                        └──▶ YouTubePlayerBridge (android-youtube-player)
        │
        └──▶ Repositories ─────────▶ MediaStoreAudioScanner & LyricsResolver (.lrc / .txt / USLT)
                                 ├──▶ Room Database (Playlists, Favorites, Recent, Custom Lyrics)
                                 ├──▶ DataStore Preferences (API Key, Visualizer & Playback Modes)
                                 └──▶ YouTubeRepository (YouTube Data API v3 + Demo Streams)
```

---

## Permissions

| Permission | Purpose |
| --- | --- |
| `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 12 and below) | Automatic scanning of local audio tracks (`MediaStore.Audio.Media`) and sibling `.lrc`/`.txt` lyrics files |
| `RECORD_AUDIO` | Required by Android OS to capture real-time audio buffer FFT/waveform data via `Visualizer` (falls back to BPM beat simulation if denied) |
| `INTERNET` | YouTube Music streaming, thumbnail loading, and YouTube Data API v3 search |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Continuous background audio playback via `MediaSessionService` |
| `POST_NOTIFICATIONS` (Android 13+) | Media playback notification and lock-screen controls |

---

## Building the APK

### Requirements
- **Android Studio** (or Android SDK with **Compile SDK 36**, **Min SDK 24**)
- **JDK 17** or newer
- **Gradle 9.3.1**

### Build Locally
1. Decode the debug keystore (first time only):
   ```bash
   tr -d '\r\n' < debug.keystore.base64 | base64 -d > debug.keystore
   ```
2. Run unit tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
3. Build the debug APK:
   ```bash
   gradle assembleDebug
   ```
   The output APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Automated GitHub Actions Builds
This repository includes `.github/workflows/build-apk.yml`, which automatically runs unit tests (`:app:testDebugUnitTest`), builds the debug APK (`gradle assembleDebug`), and uploads `synthwave-debug-apk` as a downloadable artifact on every push/PR to `main` or `master` (or on manual `workflow_dispatch`).

---

## YouTube Data API v3 Setup (Optional)
The **YouTube Music** tab works immediately using curated royalty-free demo streams. To enable full YouTube Music search:
1. Create a **YouTube Data API v3** key in the [Google Cloud Console](https://console.cloud.google.com/).
2. Open the **YouTube Music** tab in SynthWave, tap the **Settings** gear icon, and paste your API key.
