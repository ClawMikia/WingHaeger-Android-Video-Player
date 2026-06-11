# WingHaeger

**WingHaeger** is a specialized, high-performance Android media player designed to provide an inspiring, motivating, and empowering experience. Built with a futuristic HUD-inspired aesthetic, it delivers a unique way to consume positive and empowering video and audio content.

Developed with **Kotlin** and **AndroidX Media3 (ExoPlayer)**, WingHaeger prioritizes precision playback control and deep customization through a high-tech "Base Command" interface.

## 🚀 Key Features

### 🎬 Advanced Playback Engine
- **Media3 ExoPlayer Core**: High-performance, stable playback for a wide range of formats.
- **Precision Trim Zones**: Define custom Start and End points for any video to focus on specific highlights.
- **Auto-Skip Segments**: Create multiple "Skip Zones" within a video to automatically jump over irrelevant content.
- **Smart Transport Controls**: Custom seek, rewind, fast-forward, and a dedicated "Restart Mission" button.
- **Picture-in-Picture (PiP)**: Seamlessly transition to background/overlay mode for multitasking.

### 🛡️ Mission Control & Library
- **Video Vault**: Organizes your media archive with detailed metadata and folder grouping.
- **Mission Queues (Playlists)**: Create and manage custom sequences with support for sequential or random auto-launch.
- **Recall Log (Playback Memory)**: Comprehensive history tracking with "Continue Mission" support for interrupted sessions.
- **Mark & Favorite**: Quickly tag key moments with chapter markers and keep your most empowering content in the Favorites vault.

### 🎛️ Lens Matrix (Visual Enhancements)
- **Real-time Enhancements**: Apply visual filters and matrix transformations to the video stream.
- **Granular Controls**: Adjust brightness, contrast, saturation, hue, and sharpness.
- **Advanced Scaling**: Zoom and crop modes to fit any screen or focus on specific video areas.

### 🔉 Tactical Audio & Subs
- **Audio Boost & EQ**: Enhance sound quality with presets and volume boosting.
- **Dynamic Subtitles**: Full control over subtitle tracks, timing offsets, and visual styling (size, weight, background).

## 🎨 Design Philosophy: The HUD Experience

WingHaeger breaks away from traditional "clean" Material design in favor of a **Cyber-HUD (Heads-Up Display)** aesthetic:

- **Typography**: Powered by the **Orbitron** font family for a technical, futuristic feel.
- **Color Palette**: Dark "Void" backgrounds contrasted with "Neon Cyan", "Solar Gold", and "Plasma Violet" accents.
- **Interface**: Layouts are designed as "Base Panels" and "Matrix Grids," providing a tactile, tactical feel to media management.
- **System Terminology**: Media management is framed as "Loading Archives," "Managing Missions," and "Calibrating the Lens Matrix."

## 🛠️ Tech Stack

- **Language**: Kotlin 1.9+
- **Media Engine**: AndroidX Media3 (ExoPlayer 1.5.1)
- **UI Framework**: XML Layouts with ViewBinding, ConstraintLayout, and Material 3 Components.
- **Database**: SQLite (via `WingDbHelper`) with support for complex video preferences, skips, and chapter markers.
- **Concurrency**: Kotlin Coroutines & Flow.
- **Architecture**: Modular structure following modern Android best practices.

## 📥 Getting Started

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/username/WingHaeger.git
    ```
2.  **Open in Android Studio**: Ensure you have the latest stable version of Android Studio.
3.  **Sync Gradle**: The project uses modern dependencies including Media3 and Lifecycle KTX.
4.  **Run**: Deploy the `:app` module to your device or emulator.

## 📜 Permissions
WingHaeger requires the following permissions to operate as a full-featured media base:
- `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO`: To access your media archive.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: For uninterrupted background missions.
- `WAKE_LOCK`: To prevent system sleep during critical playback.

---
*Stay Inspired. Stay Empowered. Command your media with WingHaeger.*
