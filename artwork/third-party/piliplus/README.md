# PiliPlus icon font

`app/src/main/res/font/piliplus_custom_icons.ttf` is copied unchanged from PiliPlus
`assets/fonts/custom_icon.ttf`. The settings and toggle affordances use
`CustomIcons.dm_settings` (U+E804), `dm_on` (U+E803), and `dm_off` (U+E802)
from `lib/common/widgets/custom_icon.dart`, following the supplied references.

Source: https://github.com/bggRGjQaUbCoE/PiliPlus
License: GNU GPL v3; the source repository's license is included in `LICENSE`.

## 番剧标识与交互参考

`app/src/main/res/drawable/ic_bangumi_vip.xml` 由 `assets/images/big-vip.svg`
转换为 Android VectorDrawable，保留原始路径、颜色与白色描边。
番剧详情面板与选集标识行为参考 `lib/pages/video/introduction/pgc/widgets/intro_detail.dart`、
`episode.dart`、`section.dart` 及 `lib/models_new/video/video_detail/episode.dart`。
上述来源同样适用本目录保留的 GNU GPL v3 许可证。

点评列表、默认类型、作者与星级展示、排序和发布入口参考
`lib/pages/pgc_review/view.dart`、`child/view.dart`、`child/controller.dart`、`post/view.dart`
与 `lib/models_new/pgc/pgc_review/`，由本项目 Compose 组件实现。
计数与排序栏随滚动方向浮动的行为同时参考
`lib/common/widgets/sliver/sliver_floating_header.dart`。
