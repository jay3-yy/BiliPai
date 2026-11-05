<div align="center">

<img src="docs/images/233娘.jpeg" height="96" alt="BiliPai" />

# BiliPai

**A native, clean and extensible BiliBili client for Android**

[简体中文](README.md) · [English](README_EN.md)

<img src="https://img.shields.io/badge/Source-0.7.9--beta.3%20%28554%29-007AFF?style=flat-square" alt="Source 0.7.9-beta.3 (554)" />
<img src="https://img.shields.io/badge/Android-8.0%2B-34C759?style=flat-square" alt="Android 8.0+" />
<img src="https://img.shields.io/badge/License-GPLv3-FF3B30?style=flat-square" alt="GPLv3" />

[Download](#download) · [Wiki](https://github.com/jay3-yy/BiliPai/wiki) · [Telegram channel](https://t.me/bilipai666) · [Telegram group](https://t.me/bilipai888/1)

</div>

Built with Kotlin and Jetpack Compose, BiliPai supports videos, bangumi, live streams, feeds, messages and offline downloads, adapting to phones, tablets and foldables.

## Features

| Feature | Details |
| --- | --- |
| Playback | DASH, HD quality, danmaku, gestures, speed controls, resume, background play and picture-in-picture |
| Audio mode | Immersive and vinyl layouts, lyrics, queues, sleep timer, system media controls and Android Auto |
| Home screen widget | Current title, playback controls, audio resumption, and Watch later / Search shortcuts |
| Subtitles and notes | Local subtitle import/export, styling and timing; private notes, timestamps and AI-summary drafts |
| Bangumi and live | Following, adaptive episode grids, large-screen panes and landscape layouts; live categories and danmaku |
| Feeds and messages | Followed creators, comments, replies, image previews and direct messages |
| Search and downloads | Video, creator and bangumi search; resumable downloads and local playback |
| Appearance | Google Material 3, Miuix, optional Liquid Glass with elastic motion, dynamic colors, regular home grids and large-screen layouts |
| Extensions | Plugins, DLNA / Google Cast and WebDAV settings backup |

Some quality tiers require login or account privileges. Appearance, playback, danmaku and background behavior are configurable in Settings. See the [widget and Android Auto guide (Chinese)](docs/wiki/SYSTEM_MEDIA.md) for system media entry points.

## Preview

<div align="center">

**Liquid Glass, throughout the app**

Screenshots show Liquid Glass in a few parts of the app.

<img src="docs/images/screenshot-glass-1.png" height="400" alt="BiliPai home" />
<img src="docs/images/screenshot-glass-2.png" height="400" alt="BiliPai appearance settings" />
<img src="docs/images/screenshot-glass-3.png" height="400" alt="BiliPai video detail" />
<img src="docs/images/screenshot-glass-4.png" height="400" alt="BiliPai search" />

</div>

Appearance varies by version, theme and screen size.

Beyond the screenshots: tap-to-act danmaku, swipe-up fullscreen with ambient light in portrait playback, audio mode, AI note summaries, subtitle import/export, plugins, DLNA / Google Cast casting, WebDAV settings backup and more; some features vary with the Android version.

## Download

Get APKs and release notes from the [Telegram channel](https://t.me/bilipai666). Join the [Telegram group](https://t.me/bilipai888/1) for feedback.

- Requires Android 8.0+
- Sign in with a TV QR code, password, SMS code or imported cookies
- Allow installation from the source used to open the APK

## Plugins

Built-in plugins cover sponsor skipping, ad filtering, Anime4K, danmaku, eye care, recommendations and casting. Additional extension formats include:

- JSON / `.bp` rules: import by URL for content filtering, danmaku processing and more.
- `.bilipai.js` plugins: add content sources such as live streams, video catalogs and scraped web content.
- `.bpskin` skin packs: import backgrounds, colors and selected motion assets without executing code.
- `.bpplugin` Kotlin packages: package previews and capability authorization are supported; external Dex execution is not yet available.

Enable **去广告增强** (ad filtering) in the plugin center and edit **视频标签屏蔽词** to block keywords in actual video tags across home recommendations, search results and related videos. Refresh the list after changing rules. With no tag keywords, no extra tag requests are made; failed or timed-out lookups keep videos whose tags could not be checked.

Review the source and declared permissions before importing third-party plugins. See the [plugin development guide](docs/PLUGIN_DEVELOPMENT.md), [Kotlin plugin SDK](plugins/sdk/README.md) and [JS plugin examples](examples/plugins/README.md).

## Recent updates

- Ad filtering supports blocked video-tag keywords; video cards show a "竖屏" (vertical) badge by playback size; the dynamic page can hide its top uploader avatar bar.
- Tablet embedded and fullscreen players share landscape danmaku styling; the player gains standalone loading and buffering indicators.
- Returning to the story feed resumes unseen content; history is only written after real playback, and returning to details no longer sticks to the cover.
- Comment translation handles Chinese reply prefixes and mixed-language text, while Chinese-dominant comments no longer show the translate action; sub-reply details keep scroll position and like states no longer revert.
- Search input syncs one-way into the query, fixing cursors, selections and IME composition being reset by async writes.
- Fullscreen-to-mini-player transitions release the orientation lock; watch-later cards gain a remove action and the Miuix floating dock uses frosted backgrounds in both materials.

See [CHANGELOG.md](CHANGELOG.md) for details.

## Build

Use JDK 21 and Android SDK 37.

```bash
git clone https://github.com/jay3-yy/BiliPai.git
cd BiliPai
./gradlew :app:assembleDev
```

Miuix dependencies require GitHub Packages access. Set `gpr.user` and `gpr.key` in your local `~/.gradle/gradle.properties`, or use `GITHUB_ACTOR` and `GITHUB_TOKEN`. APKs are exported to `app/build/outputs/bilipai/dev/`. `app/google-services.json` is optional; Firebase is skipped when absent.

## Contributing

Share bugs and ideas through [Issues](https://github.com/jay3-yy/BiliPai/issues/new/choose), or help improve features and translations with a [pull request](https://github.com/jay3-yy/BiliPai/pulls).

## Acknowledgements

- [Kotlin](https://kotlinlang.org/) — language and coroutines
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — declarative UI framework
- [AndroidX Media3](https://github.com/androidx/media) — Media3 / ExoPlayer playback engine
- [FFmpeg](https://ffmpeg.org/) — audio decoding
- [Room](https://developer.android.com/training/data-storage/room) / [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) — local data and preferences
- [Retrofit](https://github.com/square/retrofit) / [OkHttp](https://github.com/square/okhttp) — networking
- [Coil](https://github.com/coil-kt/coil) — image loading
- [Lottie](https://github.com/airbnb/lottie-android) — vector animation
- [MaterialKolor](https://github.com/jordond/MaterialKolor) — dynamic theme colors
- [Compose Rich Editor](https://github.com/MohamedRejeb/Compose-Rich-Editor) — rich text editing
- [DanmakuRenderEngine](https://github.com/bytedance/DanmakuRenderEngine) — danmaku rendering engine
- [BiliBili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect) — BiliBili API reference
- [BiliBiliSponsorBlock](https://github.com/hanydd/BilibiliSponsorBlock) — sponsor-skip data and API
- [Miuix](https://github.com/compose-miuix-ui/miuix) — UI component library
- [Haze](https://github.com/chrisbanes/haze) — blur effects
- [PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus) — overall app interaction reference
- [Bili Pilot](https://github.com/siwei-yuan/bili-pilot) — CDN routing reference
- [ITGSA SharedViewKit](https://gitee.com/itgsa_sharedviewkit/SharedViewDemo) — system-level video-card transition SDK and integration reference
- Xiaomi HyperOS system motion — motion parameter reference

See [third-party notices](THIRD_PARTY_NOTICES.md) for all other dependencies and sources.

## License and disclaimer

Licensed under [GPLv3](LICENSE). When distributing, provide the corresponding source code and retain copyright notices as required by the license.

This is an unofficial client and is not affiliated with BiliBili. Content belongs to its respective rights holders. Follow applicable laws and platform rules. Login information is stored locally; usage statistics can be managed in Settings.

<div align="center">

<sub>Source version 0.7.9-beta.3 (554) · Updated 2026-11-06 · Made by [YangY](https://x.com/YangY_0x00)</sub>

</div>
