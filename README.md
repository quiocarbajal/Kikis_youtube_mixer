# YouTube Music Player & Playlist Compiler

A laser-focused music player and playlist builder designed around the concept of giving the user full power over filters and "new music" suggestions to avoid annoying commercial biases that result in always listening to the same songs.

Available as a Desktop curation studio and Mobile pocket player (Android, with an iPhone / iOS companion planned).

---

## 🌟 Core Pillars

### 1. "Surprise Me" Discovery Engine
The **Surprise Me** engine is designed specifically for the discovery of genuinely new music. To achieve this without falling into repetitive commercial recommendations, it is equipped with a set of inclusion seeds and **strict** exclusion filters:

* **Inclusion Seeds (AND)**:
  * **Multi-Dimensional Seeding**: Combine any number of **Artists**, **Genres**, **Song Seeds (Similar Tracks)**, **Decades** (60s to 10s), or the **Active Vibe** (artists currently in your active queue).

* **Optional Strict Exclusion Filters (NOT)**:
  * **Guaranteed "Not in Library / Liked Songs" Exclusion**: When enabled, you won't get any recommendation that you already have in any of your playlists or liked songs.
  * **Recently Heard Exclusion**: Excludes tracks heard in the last **7 days**, **30 days**, or **None**, checking both local playback history and YouTube Music cloud listening history.
  * **Excluded Modifiers**: Add specific **Artists**, **Genres**, or **Tracks** with the **NOT** modifier to strictly excluding them from the results.
  * **Permanent Artist Blacklist (Primary Artist Rule)**: When enabled, it automatically blocks any track where a blacklisted artist is the primary author (collaborations where they are only a featured guest are allowed). So you don't ever get suggestions of that artist you hate (but always gets recommended by YouTube).
  * **Original/studio versions only**:
    * **Exclude Live Versions**: You won't get any suggestions of live versions (`"live"`, `"en vivo"`, `"concert"`, `"unplugged"`).
    * **Exclude Remix / Remaster**: Eliminates club edits, remixes, and re-works when you only want original studio master tracks (`"remix"`, `"edit"`, `"dub"`, `"club mix"`).
  * **Hidden Gems (Low Popularity Only)**: Enforces an upper popularity limit ($\le 45$), with selectable targeting: **Artist** only, **Song** only, or **Both**, which gives you access to lesser-known artists and songs.

* **Diversity Protection & Quota Transparency**:
  * **Per-Artist Capping**: Enforces strict limits on the number of songs per artist (2–4 for seed artists; max 2 for discovered co-artists) so no single artist dominates the mix.

---

### 2. True Mathematical Random Shuffle
Most streaming services do not truly shuffle; they run weighted recommendation algorithms designed to minimize bandwidth costs or push promoted tracks. **YouTube Music Player** restores honest, uniform randomness:

* **Uniform Fisher-Yates Permutation**:
  * Cryptographically sound in-memory array permutation where every track has an exact $1/N$ probability of appearing in any position.
* **Intelligent Anti-Clumping**:
  * **[OPTIONAL]** Subtle statistical distribution spacing (`spacingWindow = 2`) that prevents back-to-back tracks by the same artist while maintaining strict mathematical fairness. Especially useful to randomize the list of songs output by the "Surprise Me" engine.

---

## 🎯 Simplicity & Philosophy

Beyond its two core engines, the player is designed around minimalism, distraction-free listening, and **local-first reliability with cloud synchronization**:

* **What is "Local-First Reliability"?**:
  1. **Zero-Latency Offline Curation**: Your active queue, draft compilations, and playlists are stored locally on your device (SQLite / Room on Android, `local_playlists.json` on Desktop). Drag-and-drop reordering, track moves, and playlist editing happen instantly in memory and disk without waiting for network calls or hitting YouTube API rate limits.
  2. **JSON Safety Vault (Library Backups)**: The app generates machine-readable JSON safety backups of all your playlists, liked tracks, and blacklisted artists. Your years of music curation are preserved locally in a standard format and can never be lost, overwritten, or corrupted by remote sync glitches.
  3. **1-Click Sync to YouTube Music**: Whenever you are ready, local compilations and shuffles are pushed straight to your YouTube Music account so your library stays accessible everywhere.
* **Zero Feature Bloat**:
  * No promotional banners, no algorithmic social feeds, no Discord widgets, and no cluttered equalizers. Just clean queue control, powerful discovery, and your music.

---

## 📱 Form Factors & Platform Roadmap

* [x] **Desktop (Mac / PC)**: The *"Studio & Compiler"* — optimized for keyboard and mouse navigation, drag-and-drop track sequencing, library backups, and fast compilation. Powered by an embedded local Ktor backend and responsive desktop window.
* [x] **Android**: The *"Pocket Player"* — built for distraction-free listening, lock-screen/background audio playback, and 1-tap "Surprise Me" generation on the go.
* [ ] **iPhone (iOS)**: Planned companion player sharing the same core Kotlin Multiplatform engine, true shuffle, and Surprise Me discovery logic.

---

## 🏗️ Repository Architecture

```
youtube_music_player/
├── core/         # Shared data models, Innertube client, Fisher-Yates shuffle, and discovery logic
├── desktop/      # Desktop curation studio with Ktor Netty backend & responsive web frontend
├── android/      # Native Jetpack Compose Android client with background media playback & Room DB
├── ios/          # [Planned] iPhone companion app leveraging shared :core architecture
└── figures/      # App assets and icons
```

---

## 🚀 Building & Running

### Prerequisites
- **JDK**: OpenJDK 17 or higher (tested with JDK 24).
- **Android SDK**: API 34+ (for Android builds).
- **Gradle**: Built using included `./gradlew` (Gradle 8.7).

### Desktop App
```bash
# Run the Desktop application
./gradlew :desktop:run

# Package macOS DMG installer
./package_dmg.sh
```

### Android App
```bash
# Compile Kotlin sources
./gradlew :android:compileDebugKotlin

# Install directly to connected phone or emulator via ADB
./gradlew :android:installDebug
```

---

## 👥 Project Credits

Conceived by **Quio**, designed collaboratively, and coded & assembled using AI with **Google Antigravity**.
