# BiliPai <img src="docs/images/233娘.jpeg" height="80" align="center">

<p align="center">
  <strong>Native, Pure, Extensible — Redefining your Bilibili experience</strong>
</p>

<p align="center">
  <sub>Last updated: 2026-09-11 · Current build: 0.2.3-beta.33 · Latest documented release: v0.2.3-beta.33</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Release-0.2.3--beta.33-fb7299?style=flat-square" alt="Release">
  <img src="https://img.shields.io/github/stars/jay3-yy/BiliPai?style=flat-square&color=yellow" alt="Stars">
  <img src="https://img.shields.io/github/forks/jay3-yy/BiliPai?style=flat-square&color=green" alt="Forks">
  <img src="https://img.shields.io/github/last-commit/jay3-yy/BiliPai?style=flat-square&color=purple" alt="Last Commit">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026)-brightgreen?style=flat-square" alt="Platform">
  <img src="https://img.shields.io/badge/APK-Varies-orange?style=flat-square" alt="Size">
  <img src="https://img.shields.io/badge/License-Non--Commercial-blue?style=flat-square" alt="License">
  <img src="https://img.shields.io/badge/Plugins-10%20Built--in-blueviolet?style=flat-square" alt="Plugins">
</p>

<p align="center">
  <a href="https://t.me/bilipai666"><img src="https://img.shields.io/badge/Telegram-Channel-5AC8FA?style=flat-square&logo=telegram" alt="Telegram channel"></a>
  <a href="https://t.me/bilipai888/1"><img src="https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram" alt="Telegram group"></a>
  <a href="https://x.com/YangY_0x00"><img src="https://img.shields.io/badge/X-Follow-000000?style=flat-square&logo=x" alt="X"></a>
</p>

## 🚀 Repository Navigation

| I want to… | Quick entry |
| --- | --- |
| Download and try BiliPai | [Install](#-download--install) · [Changelog](CHANGELOG.md) · [Telegram channel](https://t.me/bilipai666) · [FAQ](docs/wiki/FAQ.md) |
| Explore the project | [Device previews](#-preview) · [Features](#-features) · [Feature matrix](docs/wiki/FEATURE_MATRIX.md) · [Roadmap](docs/wiki/ROADMAP.md) |
| Read the docs | [Wiki home](docs/wiki/README.md) · [Architecture](docs/wiki/ARCHITECTURE.md) · [QA guide](docs/wiki/QA.md) |
| Build and develop | [Build](#️-build) · [Structure guidelines](STRUCTURE_GUIDELINES.adoc) · [Versioning](docs/wiki/VERSIONING.md) · [Release workflow](docs/wiki/RELEASE_WORKFLOW.md) |
| Develop plugins | [JSON plugin guide](docs/PLUGIN_DEVELOPMENT.md) · [Native plugin guide](docs/NATIVE_PLUGIN_DEVELOPMENT.md) · [Plugin SDK](plugins/sdk/README.md) · [Samples](plugins/samples/) |
| Contribute | [Contributing](#-contributing) · [Open an issue](https://github.com/jay3-yy/BiliPai/issues/new/choose) · [Pull requests](https://github.com/jay3-yy/BiliPai/pulls) · [AI / LLM entry](llms.txt) |

> [!CAUTION]
> `README`, `AI.txt`, `llms.txt`, and the Wiki are maintained periodically, but fast-moving main-branch changes can still make parts of them stale. Treat them as reference only; verify current behavior with source, `CHANGELOG.md`, and real builds.

## 📸 Preview

<p align="center">
  <img src="docs/images/screenshot1.png" alt="Preview 1" height="500">
  <img src="docs/images/screenshot2.png" alt="Preview 2" height="500">
  <img src="docs/images/screenshot3.png" alt="Preview 3" height="500">
  <img src="docs/images/screenshot4.png" alt="Preview 4" height="500">
  <img src="docs/images/screenshot5.png" alt="Preview 5" height="500">
  <img src="docs/images/screenshot6.png" alt="Preview 6" height="500">
  <img src="docs/images/screenshot7.png" alt="Preview 7" height="500">
  <img src="docs/images/screenshot8.png" alt="Preview 8" height="500">
  <img src="docs/images/screenshot9.png" alt="Preview 9" height="500">
</p>
---

## ✨ Features

### 🎬 Video Playback

| Feature | Description |
|-----|-----|
| **HD Quality** | Supports 4K / 1080P60 / HDR / Dolby Vision (Login/Premium required) |
| **DASH Streaming** | Adaptive bitrate selection, seamless quality switching, smooth playback |
| **Danmaku System** | Adjustable opacity, font size, speed, and density filtering |
| **Video Notes** | Private notes, AI-summary drafts, rich text editing, timestamps, Markdown as the editor interchange format, and system sharing |
| **Gesture Control** | Brightness (left), Volume (right), Seek (horizontal) |
| **Playback Speed** | Edit the shared speed list (0.1x–8x) under Settings → Playback; choose default playback and temporary long-press speeds independently. Player menus and two-finger speed gestures use the same list; swipe-up lock remains available. |
| **Picture-in-Picture** | Floating window playback for multitasking |
| **Audio Mode** | 🆕 Dedicated audio player with immersive/vinyl modes, lyrics, playlists, and a sleep timer |
| **In-app Update** | Check for updates from Settings; also follow the Telegram channel for releases |
| **Background Play** | Continue listening when screen is off or in background, with dedicated background-play and audio-focus toggles plus more reliable prev/next controls from notifications and system media controls |
| **Playback Order** | Supports Stop After Current / In-order / Single Loop / List Loop / Auto Continue, with quick toggle in landscape and portrait |
| **Portrait Interaction Fixes** | Fixes like/favorite actions after swiping to the next portrait video, and favorites now open the folder picker directly |
| **Seek Preview Optimization** | Preview image updates are quantized to videoshot frame boundaries to reduce redraw cost during drag/tap seeking |
| **Comment Copy UX** | Long-press opens selectable-copy panel so users can drag-select exact comment text (including rich text scenarios) |
| **Playback History** | Automatically resume playback, with a toggle and one-time prompt per target |
| **TV Login** | Scan QR code to login as TV client to unlock high quality |
| **Plugin System** | Built-in SponsorBlock, AdBlock, Danmaku Enhancement, Eye Protection, Today Watch, CDN Region, and First Visit Recommendation plugins |

### 🔌 Plugin System

| Plugin | Description |
|-----|-----|
| **SponsorBlock** | Automatically skip ads/sponsor segments based on BilibiliSponsorBlock database |
| **AdBlock** | Smartly filter commercial content from recommendation feeds |
| **Danmaku Plus** | Keyword blocking and highlighting for personalized danmaku experience |
| **Eye Protection** | Scheduled eye care, 3 presets + DIY tuning, real-time preview, warm filter, humane reminders with snooze |
| **🆕 Today Watch** | Local recommendation plugin with Relax/Learn modes, collapse/expand, independent refresh, UP ranking, and reason tags |
| **🆕 CDN Region** | Off by default; prioritizes same-region CDN candidates for normal video playback while preserving original URLs for fallback |
| **🆕 First Visit Recommendation** | Off by default; removes cookies only from the Web home recommendation API so the feed is closer to public guest recommendations |
| **Plugin Center** | Unified management for all plugins with independent configurations |
| **🆕 External Plugins** | Support loading dynamic JSON rule plugins via URL |

First Visit Recommendation credits wangdaodao's original [TabulaBili](https://github.com/wangdaodaodao/TabulaBili) and tjsky's [TabulaBili-Plus](https://github.com/tjsky/TabulaBili), adapted here as a built-in Android plugin.

#### Implemented Details (Supplement)

- `Today Watch`:
  - dual mode switch: `Relax Tonight` / `Deep Learning`
  - UP ranking + recommendation queue + per-item explanation tags
  - queue rows display uploader avatar + name for better readability
  - linked with eye-care night signal (prefers shorter, lower-stimulation content at night)
  - local negative-feedback learning (disliked video/uploader/keywords)
  - one-shot cold-start exposure strategy so users can see the card on first screen
  - one-tap reset of local profile + feedback in plugin settings
- `Eye Protection 2.0`:
  - 3 presets (`Gentle/Balanced/Focus`) + full DIY controls
  - real-time brightness and warm-filter preview
  - schedule + usage reminders + snooze
  - improved humane reminder copy and pacing strategy
- `Quality Switching`:
  - quality options now follow the API list, while real DASH tracks decide which tiers stay switchable
  - cache switching requires exact target quality match; falls back to API when missing
  - clearer fallback toast when requested quality is unavailable

#### Today Watch UI Example

<p align="center">
  <img src="docs/images/screenshot_today_watch_plan.png" alt="Today Watch screenshot" height="560">
</p>

#### Today Watch Algorithm (Detailed)

1. Inputs

- history sample from local watch history
- candidate videos from home recommend feed
- mode (`Relax` or `Learn`)
- eye-care night signal
- creator profile signals (cross-session local memory)
- penalty signals (disliked video/uploader/keywords)

2. Creator affinity build-up

- filter valid history items (`bvid` not empty, valid `owner.mid`)
- aggregate per-creator score with completion + recency bonus
- merge cross-session profile signals from local store

3. Candidate scoring

- score = base popularity + creator affinity + freshness + mode score + night adjustment + feedback penalty + seen penalty
- seen videos are explicitly penalized
- mode score differs for Relax and Learn (duration + keyword orientation)
- night adjustment favors short, low-stimulation items

4. Diversity queue

- queue is not pure score sort
- each round applies anti-streak penalties for repeated creators
- includes novelty bonus for unseen creators in the current queue

5. Explainability and privacy

- each queued item has explanation tags (e.g. `Learn · Mid Length · Night Friendly · Preferred Uploader`)
- runs fully local; no history upload for personalization
- users can clear local profile/feedback and restart recommendation learning

<details>
<summary><b>📖 JSON Rule Plugin Quick Start (Click to expand)</b></summary>

#### What is a JSON Rule Plugin?

A lightweight plugin format requiring **no coding**, just a simple JSON file to implement content filtering.

#### Plugin Structure

```json
{
    "id": "my_plugin",
    "name": "My Plugin",
    "description": "Plugin description",
    "version": "1.0.0",
    "author": "Your Name",
    "type": "feed",
    "rules": [
        {
            "field": "title",
            "op": "contains",
            "value": "Ad",
            "action": "hide"
        }
    ]
}
```

#### Supported Fields

| Type | Field | Description |
|------|------|------|
| **Feed** | `title` | Video Title |
| **Feed** | `duration` | Video Duration (seconds) |
| **Feed** | `owner.mid` | Uploader UID |
| **Feed** | `owner.name` | Uploader Name |
| **Feed** | `stat.view` | Play Count |
| **Danmaku** | `content` | Danmaku Content |

#### Operators

| Operator | Description | Example |
|--------|------|------|
| `contains` | Contains string | `"value": "Ad"` |
| `regex` | Regular expression | `"value": "Shocking.*Must Watch"` |
| `lt` / `gt` | Less than / Greater than | `"value": 60` |
| `eq` / `ne` | Equal / Not Equal | `"value": 123456` |
| `startsWith` | Starts with | `"value": "【"` |

#### Example: Short Video Filter

```json
{
    "id": "short_video_filter",
    "name": "Short Video Filter",
    "type": "feed",
    "rules": [
        { "field": "duration", "op": "lt", "value": 60, "action": "hide" }
    ]
}
```

#### Installation

1. Upload the JSON file to a publicly accessible URL (e.g., GitHub Gist)
2. In BiliPai, go to **Settings → Plugin Center → Import External Plugin**
3. Paste the URL and install

</details>

> 📚 **Full Documentation**: [Plugin Development Guide](docs/PLUGIN_DEVELOPMENT.md)
>
> 🧩 **Sample Plugins**: [plugins/samples/](plugins/samples/)

### 📺 Anime / Bangumi

| Feature | Description |
|-----|-----|
| **Bangumi Home** | Hot recommendations, schedule, categorical browsing |
| **Episode Selection** | Official style bottom sheet for switching episodes/seasons |
| **Landscape Top Bar Actions** | Like / coin / share are now available in landscape/fullscreen and stay closer to the regular video player behavior |
| **Tracking** | Watch list management and progress synchronization |
| **Danmaku** | Full danmaku support for anime |

### 📡 Live Streaming

| Feature | Description |
|-----|-----|
| **Live List** | Hot live streams, categories, followed streamers |
| **HD Streaming** | HLS adaptive bitrate playback |
| **Live Danmaku** | Real-time danmaku display |
| **Quick Access** | Jump to live room directly from dynamic cards |

### 📱 Dynamic Feed

| Feature | Description |
|-----|-----|
| **Feeds** | View videos/posts/reposts from followed uploaders |
| **Filtering** | Switch between All / Video Only |
| **GIF Support** | Perfect rendering of GIF images in dynamic posts |
| **Image Download** | Long press to preview and save to gallery |
| **Image Preview** | Global non-dialog overlay with iOS-style open/close motion; comment scene uses top caption to avoid covering image content, with 3D-like text transition |
| **@ Highlighting** | Auto-highlight @User mentions |

### 💬 Message Center & Direct Messages

| Feature | Description |
|-----|-----|
| **Message Center** | Unified entry for replies, mentions, likes, and system notices |
| **History List** | View session history with pagination |
| **Rich Content** | Supports stickers, mentions, and image viewing |
| **Video Link Preview** | Detects BV links and renders inline preview cards |
| **Deep Link Routing** | Opens video, dynamic, space, live, bangumi, music, and web targets directly from messages |

### 📥 Offline Cache

| Feature | Description |
|-----|-----|
| **Download** | Select quality, auto-merge audio/video |
| **Resumable** | Auto-resume downloads after network interruption |
| **Management** | Clear download list and progress display |
| **Local Playback** | Manage and play offline videos |

### 🔍 Smart Search

| Feature | Description |
|-----|-----|
| **Real-time Suggestions** | Search suggestions while typing (300ms debounce) |
| **Trending** | Display current hot search terms |
| **History** | Auto-save search history with deduplication |
| **Categories** | Search by Video / Uploader / Anime |

### 🎨 Modern UI Design

| Feature | Description |
|-----|-----|
| **Material You** | Dynamic theming based on wallpaper |
| **Dark Mode** | Perfect dark mode support |
| **iOS Style Bar** | Elegant frosted glass navigation bar |
| **Animations** | Wave entrance, elastic scaling, shared element transitions |
| **Shimmer** | Elegant loading placeholders |
| **Lottie** | Beautiful interactions for Like/Coin/Fav |
| **Celebration** | Particle effects for successful interactions |

### 👤 Profile

| Feature | Description |
|-----|-----|
| **Login Methods** | TV QR, phone/password, SMS verification, and cookie import |
| **Info** | Avatar, nickname, level, coin display |
| **History** | Local history browsing, deletion, and article-aware navigation; cloud synchronization remains planned |
| **Favorites** | Manage favorites and playlists |
| **Following** | Browse following/fans list |

### 🔒 Privacy Friendly

- 🚫 **No Ads** - Pure viewing experience, no ad injections
- 🔐 **Minimal Permissions** - Only essential permissions (No Location/Contacts/Phone)
- 💾 **Local Storage** - Login credentials stored locally, no privacy data upload
- 🛡️ **More conservative telemetry defaults** - Crash tracking stays on by default, usage analytics is off by default, and player diagnostic logging remains separately available for troubleshooting
- 🪵 **Runtime logs no longer persist by default** - Ordinary runtime logs are no longer written to disk by default, while crash snapshots and manual export remain available
- 🔍 **Open Source** - Full source code available for review

---

## 📦 Download & Install

<p>
  <a href="https://t.me/bilipai666"><img src="https://img.shields.io/badge/Telegram-Channel-5AC8FA?style=for-the-badge&logo=telegram" alt="Telegram channel"></a>
  <a href="https://t.me/bilipai888/1"><img src="https://img.shields.io/badge/Telegram-Group-2CA5E0?style=for-the-badge&logo=telegram" alt="Telegram group"></a>
</p>

| Channel | Link |
| --- | --- |
| Announcements / releases | [t.me/bilipai666](https://t.me/bilipai666) |
| Community group | [t.me/bilipai888](https://t.me/bilipai888/1) |
| Source code | [GitHub](https://github.com/jay3-yy/BiliPai) |

### Requirements

| Item | Requirement |
|-----|-----|
| **Android Version** | Android 8.0+ (API 26) |
| **Architecture** | 64-bit (arm64-v8a) |
| **Recommended** | Android 12+ for full Material You experience |
| **Size** | Varies by ABI/build variant |

### Installation

1. Download the latest APK from the [Telegram channel](https://t.me/bilipai666) or [group](https://t.me/bilipai888/1)
2. Install on your device (Unknown Sources permission may be required)
3. Open the app and sign in with TV QR, phone/password, SMS verification, or cookie import
4. Enjoy the pure Bilibili experience!

---

## 🧱 Project Structure

```text
BiliPai/
├── app/                         # App shell, product features, navigation, player, state and tests
│   └── src/main/java/com/android/purebilibili/
│       ├── app/                 # Application startup and top-level assembly
│       ├── core/                # Shared network, storage, player, plugin, theme and UI capabilities
│       ├── data/                # API/database models and repositories
│       ├── domain/              # Reusable use cases and business policies
│       ├── feature/             # Home, video, dynamic, live, settings and other product areas
│       ├── navigation/          # Route compatibility and top-level navigation policies
│       └── navigation3/         # NavKey, back stack, entries/scenes and predictive back
├── design-system/               # Shared MD3, Miuix and iOS visual primitives and policies
├── settings-core/               # Reusable settings policies
├── network-core/                # Reusable network fallback and feed policies
├── plugin-sdk/                  # Recommendation, player and danmaku plugin contracts
├── baselineprofile/             # Startup and frame-timing benchmarks/profiles
├── docs/                        # Wiki, plugin guides and image assets
├── plugins/                     # SDK docs, JSON/source samples, skins and community index
└── scripts/                     # CI, release, profiling and device collection tools
```

---

## 🛠 Tech Stack

### Core Framework

| Category | Technology | Description |
|-----|-----|-----|
| **Language** | Kotlin 2.4 | AGP built-in Kotlin toolchain |
| **Build Baseline** | AGP 9.3.1 / Gradle 9.5 / Kotlin 2.4 / JDK 21 | compileSdk 37, minSdk 26 |
| **UI** | Jetpack Compose | Material 3, Miuix, Compose Cupertino |
| **Navigation** | Navigation3 1.2.0-alpha07 | App-owned back stack, scenes and predictive back |
| **Architecture** | MVVM + Clean Architecture | Clear separation, maintainable |

### Network & Data

| Category | Technology | Description |
|-----|-----|-----|
| **Network** | Retrofit + OkHttp | RESTful API |
| **Serialization** | Kotlinx Serialization | JSON parsing |
| **Storage** | Room + DataStore | Database + Preferences |
| **Image** | Coil Compose | GIF support |

### Media

| Category | Technology | Description |
|-----|-----|-----|
| **Player** | ExoPlayer (Media3) | DASH / HLS / MP4 |
| **Danmaku** | DanmakuRenderEngine + app policies | GPU rendering, filtering, layout, and live-overlay integration |
| **Decoding** | MediaCodec | Hardware acceleration |

### UI Enhancements

| Category | Technology | Description |
|-----|-----|-----|
| **Animation** | Compose Animation / SharedTransition + Lottie | Card morphs, predictive back and vector motion |
| **Blur** | Haze 2 + Miuix Backdrop / Liquid Glass | Frosted and liquid-glass surfaces with fallbacks |
| **Theming** | Material 3 + Miuix + iOS preset | Dynamic color, dark mode and adaptive components |

---

## 📚 Wiki

- AI / LLM Entry: [`llms.txt`](llms.txt)
- Current Roadmap: [`docs/wiki/ROADMAP.md`](docs/wiki/ROADMAP.md)
- Compatibility alias: `AI.txt`
- AI Navigation Guide: [`docs/wiki/AI.md`](docs/wiki/AI.md)
- Wiki Home: [`docs/wiki/README.md`](docs/wiki/README.md)
- Feature Matrix: [`docs/wiki/FEATURE_MATRIX.md`](docs/wiki/FEATURE_MATRIX.md)
- Architecture: [`docs/wiki/ARCHITECTURE.md`](docs/wiki/ARCHITECTURE.md)
- Release Workflow: [`docs/wiki/RELEASE_WORKFLOW.md`](docs/wiki/RELEASE_WORKFLOW.md)
- QA Checklist: [`docs/wiki/QA.md`](docs/wiki/QA.md)

---

## 🗺️ Roadmap

> [!TIP]
> This summary reflects the current direction. For implemented behavior and source status, prefer the code and `CHANGELOG.md`.

| Status | Direction |
| --- | --- |
| Product baseline | Home, playback, bangumi, live, dynamic feed, messages, offline/audio mode, video notes, casting, WebDAV, account sessions, plugins, large-screen layouts and three visual presets |
| Current P0 | End-to-end video-card/predictive-return acceptance, transition steady-state performance, Navigation3 1.2 device regression, and restoration of the AGP 9 unit-test pipeline |
| Next | Controlled external-plugin execution, per-account data isolation, favorites management, complete localization, and evaluation of history cloud sync |

See the [current roadmap](docs/wiki/ROADMAP.md) for priorities, completion criteria, guardrails, and non-goals.

---

## 🔄 Changelog

See full changelog: [CHANGELOG.md](CHANGELOG.md)

### Current source build (v0.2.3-beta.33 · 2026-09-11)

- Current source build: `0.2.3-beta.33` / `versionCode 357`.
- Segmented navigation prevents right-angle protrusion during scrolling; category and filter bars upgraded to adaptive sliding liquid track; long labels fully visible without truncation.
- Search results page adapted for tablets and foldables with multi-column grid layout and user-customizable column counts.
- Dynamic feed detail and incremental refresh refactored with fallback recovery for offline stability; enriched rich text and comments panel interactions.
- Danmaku blocking rules support regex and advanced prefix syntax with smart retention of curly brace quantifier commas.
- Fixed Samsung Android 13 crashes and foldable cover-screen landscape cold-start jitter; smoother card predictive-return curves and squircle transitions.
- See [CHANGELOG.md](CHANGELOG.md) for the complete beta.26 → beta.27 notes.
- Official Telegram: channel [@bilipai666](https://t.me/bilipai666), group [@bilipai888](https://t.me/bilipai888/1).

---

## 🏗️ Build

```bash
git clone https://github.com/jay3-yy/BiliPai.git
cd BiliPai
./gradlew :app:assembleDev
```

The installable artifact is exported to `app/build/outputs/bilipai/dev/BiliPai-0.2.3-beta.33-dev.apk`. Release builds use `app/build/outputs/bilipai/release/BiliPai-0.2.3-beta.33.apk`; AGP's internal `app-*.apk` files are not delivery artifacts.

---

## 🤝 Contributing

Issues and Pull Requests are welcome!

1. Fork the repository
2. Create feature branch
3. Commit changes
4. Push to branch
5. Submit Pull Request

---

## 🙏 Acknowledgements

| Project | Description |
|-----|-----|
| [PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus) | Playback flow, comment presentation, and mobile UX reference |
| [Bili Pilot](https://github.com/siwei-yuan/bili-pilot) | Signed CDN candidate, segment-level routing, and prefetch design reference; independently implemented in Kotlin without copying its JavaScript |
| [biliSendCommAntifraud](https://github.com/freedom-introvert/biliSendCommAntifraud) | Reference implementation for comment anti-fraud detection |
| [BilibiliSponsorBlock](https://github.com/hanydd/BilibiliSponsorBlock) | Sponsor skip segment data and API reference |
| [DanmakuRenderEngine](https://github.com/bytedance/DanmakuRenderEngine) | High-performance danmaku rendering engine |
| [Miuix](https://github.com/compose-miuix-ui/miuix) | Miuix-style Compose Multiplatform components |
| [Haze](https://github.com/chrisbanes/haze) | Blur and frosted-glass effects |
| [Compose Cupertino](https://github.com/alexzhirkevich/compose-cupertino) | Cupertino-style Compose UI components |

---

## ⚠️ Disclaimer

> [!CAUTION]
>
> 1. This project is for **learning purposes only**. Commercial use is strictly prohibited.
> 2. Data source: Bilibili Official API. Copyright belongs to Shanghai Hupu Information Technology Co., Ltd.
> 3. Login info is stored locally and never uploaded.
> 4. Please comply with local laws and regulations.
> 5. Contact for deletion if copyright infringement occurs.

---

## 📄 License

[BiliPai Non-Commercial License 1.0](LICENSE)

You may use, copy, modify, build, and distribute this project or modified versions for non-commercial purposes. Modified versions may be distributed as closed-source works, and no source disclosure or acknowledgement is required.

Commercial use, paid distribution, commercial services, ad monetization, or any other profit-oriented use requires separate prior written permission from the copyright holder.

---

## ☕ Support

If you like BiliPai, buy me a coffee ☕

<p align="center">
  <img src="docs/donate.jpg" alt="Donation" width="300">
</p>
