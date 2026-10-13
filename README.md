<p align="center">
  <img src="branding/arc-music-wordmark.svg" alt="Arc Music Wordmark" width="480">
</p>

<p align="center">
  <strong>A modern, audiophile-grade music player for Android built with Jetpack Compose, AGSL runtime shaders, tactile physics, and real-time karaoke lyrics.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_12%2B_(API_31--36)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose">
  <img src="https://img.shields.io/badge/Engine-Media3_ExoPlayer_1.5-FF6F00?style=flat-square&logo=googleplay&logoColor=white" alt="Media3">
  <img src="https://img.shields.io/badge/Database-Room_v25-007ACC?style=flat-square&logo=sqlite&logoColor=white" alt="Room">
  <img src="https://img.shields.io/badge/Scope-Personal_Project-5C86A8?style=flat-square" alt="Scope">
</p>

---

## ✨ Features at a Glance

### 🎨 Hyper-Fluid Aesthetics & GPU Shaders
- **AGSL Runtime Shaders**: Custom hardware-accelerated glassmorphism, dynamic lens blur, noise grain, and light diffraction powered by a vendored Kyant0 Backdrop engine.
- **Ambient Glow Backdrop**: Fluid background canvas extracting dominant color accents from album artwork in real time, with automatic status bar contrast compensation.
- **Tactile Spring & Jiggle Dynamics**: Interactive physics on navigation pills, seekbars, and menu surfaces for a responsive, organic feel.
- **Morphing Play/Pause Vector**: Seamless vector shape morphing powered by `androidx.graphics.shapes` and Lucide icons.
- **AMOLED True-Black Mode**: High-efficiency true-black interface crafted for OLED displays with theme-aware glass adaptation.

### 🎤 Next-Gen Karaoke Lyrics Engine
- **Word-by-Word Syllable Sync**: Real-time karaoke typography with smooth wipe-fill glow and syllable elevation for held notes.
- **Multi-Source Fetching Pipeline**:
  1. Embedded audio metadata & local `.lrc` sidecars
  2. [LyricsPlus](https://lyricsplus.prjktla.my.id) (word-level synced timing)
  3. [LRCLIB](https://lrclib.net) (line-level synced timing)
- **Bespoke Display Modes**:
  - `FADE`: Cumulative word-fill highlighting without blur (clean & crisp).
  - `DISTANCE_BLUR`: Focused spotlight line with progressive depth-of-field blur on neighboring lines.
- **Interactive Controls**: Two-finger tap or click to scrub playback directly to any lyric line, plus fine-tuned manual sync offset controls (± ms).

### 🎧 Audiophile Playback Pipeline
- **Media3 ExoPlayer Core**: Low-latency audio pipeline optimized for FLAC, ALAC, Opus, AAC, and fragmented MP4 files.
- **Direct USB DAC Routing**: Bit-perfect hardware output via `AudioDeviceCallback` and automated device sink preference routing.
- **10-Band Equalizer & DSP**: Hardware-calibrated 10-band EQ, BassBoost, Virtualizer, and LoudnessEnhancer with automatic audio offload safety bypassing.
- **Fragmented MP4 / EAC-3 Fallback Parser**: Custom atom parser (`moov`, `moof`, `traf`, `tfdt`) that extracts exact durations and bitrates from fragmented EAC-3 JOC / DASH audio streams where standard Android APIs fail.
- **Audiophile Badging**: Instant UI indicators for Hi-Res Audio (24-bit/96kHz+), Dolby Atmos / EAC-3, sample rate, bit depth, and codec.

### 🎬 Motion Canvas & Video Backdrops
- **Cinematic Motion Artwork**: High-definition looping video canvases enrich the playback screen for supported tracks.
- **Smart Local Caching**: Automated disk caching via `CanvasCacheManager` ensures instant, zero-data replays when offline.

### 📡 Offline Tap-to-Share (NFC + Nearby Connections)
- **One-Tap NFC Handshake**: Android Host Card Emulation (HCE) and NFC Reader mode coordinate secure P2P pairing tokens.
- **High-Speed Direct Transfer**: Peer-to-peer Wi-Fi Direct and Bluetooth transfer powered by Google Play Services Nearby Connections for songs, albums, and playlists.
- **Zero Internet Required**: Share complete losslessly tagged tracks with artwork between phones completely offline.

### 🩺 Collection Health & Discography Growth
- **Collection Health Audit**: Scans local media library for:
  - Missing album artwork and missing tag metadata
  - Corrupted audio tags and low-bitrate / low-quality tracks
  - Duplicate songs with an integrated cleanup & deduplication flow
- **MusicBrainz Discography Scanner**: Detects missing tracks and unowned albums for favorited artists with 7-day intelligent caching and rate-limiting.
- **Collection Growth Radar**:
  - **New Releases**: Releases from your favorite artists in the last 90 days.
  - **Complete Collection**: Album recommendations to complete partially owned discographies.
  - **Artist Discovery**: Intelligent similar artist suggestions via Last.fm.

### 📊 Listening Analytics & Insights
- **In-Depth Listening Stats**: Aggregates play history into daily, weekly, monthly, and all-time listening time.
- **Top Artists & Top Genres**: Real rankings based on actual accumulated listening minutes.
- **Night Owl Heatmap**: 24-hour hour-of-day listening distribution chart.
- **Full History**: Complete chronological log of completed and skipped tracks.

### 🔄 In-App Seamless Updates
- Built-in GitHub release integration (`aesw0x/arcmusic`) automatically checks for new versions, downloads update packages, and prompts for one-tap installations.

---

## 🛠️ Tech Stack & Architecture

Arc Music follows modern Android Architecture guidelines with clean separation of concerns:

```
app/src/main/java/com/aeswox/arcmusic/
├── ArcMusicApplication.kt          # Hilt Application & Coil ImageLoader configuration
├── MainActivity.kt                 # NavHost, System Chrome, NFC policy, BottomSheet scaffold
├── ArcNowPlayingScreen.kt          # Fullscreen player, queue, audio specs, canvas renderer
├── ArcLyricEngine.kt               # Syllable-level karaoke engine with AGSL effects
├── MusicViewModel.kt               # Central UI state coordinator
├── backdrop/                       # Vendored Kyant0 AGSL runtime shader engine
├── data/
│   ├── MediaStoreScanner.kt        # Robust scoped storage scanner & MP4 atom duration parser
│   ├── SettingsRepository.kt       # DataStore preferences
│   └── network/                    # MusicBrainz, Deezer, Last.fm, TheAudioDB, LyricsPlus, LRCLIB
├── db/
│   ├── MusicDatabase.kt            # Room Database v25 (14 tables + FTS4 search)
│   ├── MusicRepository.kt          # Local caching, gap detection, and queries
│   └── entities/                   # Relational domain models
├── playback/
│   ├── PlaybackService.kt          # Media3 MediaSessionService & ExoPlayer
│   ├── MusicPlayerConnection.kt    # Singleton UI-to-service bridge & history logging
│   ├── EqualizerManager.kt         # 10-Band EQ, BassBoost, Virtualizer DSP
│   └── UsbDacOutputManager.kt      # Direct bit-perfect USB DAC routing
└── sharing/
    ├── NearbySharingManager.kt     # Google Nearby Connections P2P transfer
    └── NfcShareService.kt          # HCE NFC tap-to-share service
```

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: Version 17 or 21
- **Android Device / Emulator**: Running **Android 12 (API 31)** or newer

### Building Locally

1. **Clone the repository**:
   ```bash
   git clone https://github.com/aesw0x/arcmusic.git
   cd arcmusic
   ```

2. **Set up environment secrets (Optional)**:
   Copy `.env.example` to `.env` to configure optional third-party API keys:
   ```bash
   cp .env.example .env
   ```
   *(Features like Last.fm discovery, Fanart.tv artist banners, and Gemini AI search will activate when corresponding keys are provided in `.env` or in **Settings**).*

3. **Open in Android Studio**:
   - Open Android Studio $\rightarrow$ **File** $\rightarrow$ **Open** $\rightarrow$ select the `arcmusic` directory.
   - Allow Gradle to sync dependencies.

4. **Run the app**:
   - Select your target device or emulator (API 31+).
   - Press **Run** (`Shift + F10`) or execute:
     ```bash
     ./gradlew assembleDebug
     ```

---

## 🔐 Permissions & Privacy

Arc Music is **offline-first** and prioritizes user privacy. Your local files and listening history never leave your device.

| Permission | Purpose |
| :--- | :--- |
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | Required to scan and play your local audio library. |
| `NEARBY_WIFI_DEVICES` / `BLUETOOTH` / `ACCESS_FINE_LOCATION` | Required strictly for Google Nearby Connections to discover and transfer music P2P to nearby devices. |
| `NFC` | Used for instant tap-to-share pairing handshakes with another phone. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required for continuous background playback and lockscreen media notifications. |
| `INTERNET` | Used strictly for online metadata enrichment (artwork, lyrics, and optional canvas video loops). |

---

## 📄 Scope & Acknowledgements

Arc Music is an independent personal music player created by **aesw0x**. All rights reserved.

### Third-Party Acknowledgements
- Portions of the GPU shader pipeline are vendored from [Kyant0/backdrop](https://github.com/Kyant0/backdrop) (Apache License 2.0).
- Iconography inspired by [Lucide Icons](https://lucide.dev) & [Hugeicons](https://hugeicons.com).
- Metadata & lyrics services provided by [MusicBrainz](https://musicbrainz.org), [LRCLIB](https://lrclib.net), [LyricsPlus](https://lyricsplus.prjktla.my.id), and [TheAudioDB](https://www.theaudiodb.com).

---

<p align="center">
  Crafted with care for music lovers and audiophiles.
</p>
