# BiliPai <img src="docs/images/233娘.jpeg" height="80" align="center">

<p align="center">
  <strong>Native, Pure, Extensible — Redefining your Bilibili experience</strong>
</p>

<p align="center">
  <sub>Last updated: 2026-04-03 · Synced to v7.3.3 (source of truth: <a href="CHANGELOG.md">CHANGELOG</a> + code)</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-7.3.3-fb7299?style=flat-square" alt="Version">
  <img src="https://img.shields.io/github/stars/jay3-yy/BiliPai?style=flat-square&color=yellow" alt="Stars">
  <img src="https://img.shields.io/github/forks/jay3-yy/BiliPai?style=flat-square&color=green" alt="Forks">
  <img src="https://img.shields.io/github/last-commit/jay3-yy/BiliPai?style=flat-square&color=purple" alt="Last Commit">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026)-brightgreen?style=flat-square" alt="Platform">
  <img src="https://img.shields.io/badge/APK-Varies-orange?style=flat-square" alt="Size">
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" alt="License">
  <img src="https://img.shields.io/badge/Plugins-5%20Built--in-blueviolet?style=flat-square" alt="Plugins">
</p>

<p align="center">
  <a href="https://t.me/BiliPai"><img src="https://img.shields.io/badge/Telegram-Join-2CA5E0?style=flat-square&logo=telegram" alt="Telegram"></a>
  <a href="https://x.com/YangY_0x00"><img src="https://img.shields.io/badge/X-Follow-000000?style=flat-square&logo=x" alt="X"></a>
</p>

## 🚀 Quick Links

| Category | Entry |
| --- | --- |
| Get Started | [Download Releases](https://github.com/jay3-yy/BiliPai/releases) · [Changelog](CHANGELOG.md) |
| Docs | [Wiki Home](docs/wiki/README.md) · [AI / LLM Entry](llms.txt) · [AI Navigation Guide](docs/wiki/AI.md) |
| Developer Reference | [JSON Plugin Guide](docs/PLUGIN_DEVELOPMENT.md) · [Native Plugin Guide](docs/NATIVE_PLUGIN_DEVELOPMENT.md) |

> [!CAUTION]
> `README`, `AI.txt`, `llm.txt`, `llms.txt`, and the Wiki are maintained periodically, but fast-moving main-branch changes can still make parts of them stale. Treat them as reference only; verify current behavior with source, `CHANGELOG.md`, and real builds.

## 📸 Preview

<p align="center">
  <img src="docs/images/screenshot_preview_1.png" alt="Preview 1" height="500">
  <img src="docs/images/screenshot_preview_2.png" alt="Preview 2" height="500">
  <img src="docs/images/screenshot_preview_3.png" alt="Preview 3" height="500">
  <img src="docs/images/screenshot_preview_4.png" alt="Preview 4" height="500">
  <img src="docs/images/screenshot_preview_5.png" alt="Preview 5" height="500">
</p>
---

## ✨ Features

### 🎬 Video Playback

| Feature | Description |
|-----|-----|
| **HD Quality** | Supports 4K / 1080P60 / HDR / Dolby Vision (Login/Premium required) |
| **DASH Streaming** | Adaptive bitrate selection, seamless quality switching, smooth playback |
| **Danmaku System** | Adjustable opacity, font size, speed, and density filtering |
| **Gesture Control** | Brightness (left), Volume (right), Seek (horizontal) |
| **Playback Speed** | 0.5x / 0.75x / 1.0x / 1.25x / 1.5x / 2.0x, with swipe-up lock while long-press speed is active |
| **Picture-in-Picture** | Floating window playback for multitasking |
| **Audio Mode** | 🆕 Dedicated audio player with immersive/vinyl modes, lyrics, playlists, and a sleep timer |
| **In-app Update** | 🆕 Check updates, download APK in-app, and hand off to the system installer |
| **Background Play** | Continue listening when screen is off or in background, with dedicated background-play and audio-focus toggles plus more reliable prev/next controls from notifications and system media controls |
| **Playback Order** | Supports Stop After Current / In-order / Single Loop / List Loop / Auto Continue, with quick toggle in landscape and portrait |
| **Portrait Interaction Fixes** | Fixes like/favorite actions after swiping to the next portrait video, and favorites now open the folder picker directly |
| **Seek Preview Optimization** | Preview image updates are quantized to videoshot frame boundaries to reduce redraw cost during drag/tap seeking |
| **Comment Copy UX** | Long-press opens selectable-copy panel so users can drag-select exact comment text (including rich text scenarios) |
| **Playback History** | Automatically resume playback, with a toggle and one-time prompt per target |
| **TV Login** | Scan QR code to login as TV client to unlock high quality |
| **Plugin System** | Built-in SponsorBlock, AdBlock, Danmaku Enhancement, Eye Protection, and Today Watch plugins |

### 🔌 Plugin System

| Plugin | Description |
|-----|-----|
| **SponsorBlock** | Automatically skip ads/sponsor segments based on BilibiliSponsorBlock database |
| **AdBlock** | Smartly filter commercial content from recommendation feeds |
| **Danmaku Plus** | Keyword blocking and highlighting for personalized danmaku experience |
| **Eye Protection** | Scheduled eye care, 3 presets + DIY tuning, real-time preview, warm filter, humane reminders with snooze |
| **🆕 Today Watch** | Local recommendation plugin with Relax/Learn modes, collapse/expand, independent refresh, UP ranking, and reason tags |
| **Plugin Center** | Unified management for all plugins with independent configurations |
| **🆕 External Plugins** | Support loading dynamic JSON rule plugins via URL |

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
  - switchable quality list now prioritizes real DASH tracks
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
| **Dual Login** | QR Code / Web Login |
| **Info** | Avatar, nickname, level, coin display |
| **History** | Auto-record watch history with cloud sync support |
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

<a href="https://github.com/jay3-yy/BiliPai/releases">
  <img src="https://img.shields.io/badge/Download-Latest%20Release-fb7299?style=for-the-badge&logo=github" alt="Download">
</a>

### Requirements

| Item | Requirement |
|-----|-----|
| **Android Version** | Android 8.0+ (API 26) |
| **Architecture** | 64-bit (arm64-v8a) |
| **Recommended** | Android 12+ for full Material You experience |
| **Size** | Varies by ABI/build variant |

### Installation

1. Download the latest APK from [Releases](https://github.com/jay3-yy/BiliPai/releases)
2. Install on your device (Unknown Sources permission may be required)
3. Open app, login via QR code or Web
4. Enjoy the pure Bilibili experience!

---

## 🛠 Tech Stack

### Core Framework

| Category | Technology | Description |
|-----|-----|-----|
| **Language** | Kotlin 1.9+ | 100% Kotlin |
| **UI** | Jetpack Compose | Declarative UI, Material 3 |
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
| **Danmaku** | DanmakuFlameMaster | Official Bilibili engine |
| **Decoding** | MediaCodec | Hardware acceleration |

### UI Enhancements

| Category | Technology | Description |
|-----|-----|-----|
| **Animation** | Lottie Compose | High quality vector animations |
| **Blur** | Haze | iOS style frosted glass |
| **Theming** | Material 3 | Dynamic color extraction |

---

## 📚 Wiki

- AI / LLM Entry: [`llms.txt`](llms.txt)
- Compatibility aliases: `AI.txt` / `llm.txt`
- AI Navigation Guide: [`docs/wiki/AI.md`](docs/wiki/AI.md)
- Wiki Home: [`docs/wiki/README.md`](docs/wiki/README.md)
- Feature Matrix: [`docs/wiki/FEATURE_MATRIX.md`](docs/wiki/FEATURE_MATRIX.md)
- Architecture: [`docs/wiki/ARCHITECTURE.md`](docs/wiki/ARCHITECTURE.md)
- Release Workflow: [`docs/wiki/RELEASE_WORKFLOW.md`](docs/wiki/RELEASE_WORKFLOW.md)
- QA Checklist: [`docs/wiki/QA.md`](docs/wiki/QA.md)

---

## 🗺️ Roadmap

> [!TIP]
> Roadmap last refreshed on 2026-04-03 (v7.3.3). For current behavior, prefer the latest release notes, `CHANGELOG.md`, and code.

### ✅ Completed

- [x] Home Waterfall Feed
- [x] Video Player + Danmaku + Gestures + PiP + Background Play
- [x] Audio Mode + Favorites/Watch Later playlist + Sequential/Shuffle/Repeat-one
- [x] Anime/Movie Playback
- [x] Live Streaming
- [x] Dynamic Feed (with fast-switch stability improvements)
- [x] Offline Download + current-video batch caching
- [x] Search + History (avoid "Delete All" for now)
- [x] Native article search + article detail + article-aware history navigation
- [x] Material You + Dark Mode
- [x] TV Login + first-play quality auth fixes for logged-in non-premium users
- [x] Landscape player controls upgrade (subtitle panel + more panel + play-order quick switch)
- [x] Shared Element Transitions + return-to-home animation optimization
- [x] Tablet/Foldable Support (sidebar + bottom bar layout)
- [x] In-app update flow (manual + auto-check + startup prompt + in-app download/install)
- [x] Plugin System Core
- [x] Built-in Plugins
- [x] Message Center category pages (Replies / Mentions / Likes / System Notices) with deep-link routing
- [x] Portrait video like/favorite interaction fixes with favorite-folder sync
- [x] Seek preview redraw optimization and cross-tab bottom-bar switching polish

### 🚧 WIP

- [ ] Wiki and module-level documentation expansion

### 📋 Planned

- [ ] History Cloud Sync
- [ ] Favorites Management
- [ ] Multi-account
- [ ] English/Traditional Chinese Support

---

## 🔄 Changelog

See full changelog: [CHANGELOG.md](CHANGELOG.md)

### Latest (v7.3.3 · 2026-04-02)

- 🔒 **Privacy defaults tightened further**: crash tracking stays enabled by default, usage analytics is disabled by default, and player diagnostic logging remains available for black-screen, stutter, and quality-switch troubleshooting.
- 🪵 **Ordinary runtime logs no longer persist by default**: warnings and errors can still feed crash snapshots and manual exports, but everyday runtime logs are no longer written to `runtime.log` automatically.
- 🕵️ **Analytics and Crashlytics are further redacted**: no more uploading video IDs, live room IDs, season IDs, target user IDs, titles, or uploader names; Crashlytics also no longer binds `mid` or stores live-room titles and anchor names.
- 🎬 **Quality switching and playback history are more stable**: premium quality switching now shows a cooldown hint instead of walking into a dead end, and playback heartbeat reports more accurate session start time, real watch time, and final progress.
- 🎚️ **Seek and playback-button feedback feel more natural**: scrubbing now preserves the user intent captured at interaction start, and the center play button keeps a more reasonable playing state while playback is resuming through buffering.
- 💬 **Comment badges and long-press menus are refined**: comment level badges now use more PiliPlus-like pixel assets including a dedicated senior level-6 badge, and video-card long-press menus appear closer to the actual press location.
- 📥 **Range-download validation is stricter**: multi-thread downloads now verify `206 Partial Content` responses and matching ranges, then fall back to single-thread download when the server responds incorrectly.

---

## 🏗️ Build

```bash
git clone https://github.com/jay3-yy/BiliPai.git
cd BiliPai
./gradlew assembleDebug
```

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
| [biliSendCommAntifraud](https://github.com/freedom-introvert/biliSendCommAntifraud) | Reference implementation for comment anti-fraud detection |

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

[GPL-3.0 License](LICENSE)

---

## ☕ Support

If you like BiliPai, buy me a coffee ☕

<p align="center">
  <img src="docs/donate.jpg" alt="Donation" width="300">
</p>
