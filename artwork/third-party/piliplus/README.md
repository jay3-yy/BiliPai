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

## 客户端专用视觉资源

从本地 PiliPlus 源码 `d635f20e22becbc6587c05c7a03671a4a9a3db9f` 导入以下资源，保留原始文件内容：

- `assets/images/ai.png` → `drawable-nodpi/bili_ai_summary.png`。
- `assets/images/trending_banner.png` → `drawable-nodpi/bili_trending_banner.png`。
- `assets/images/topic-header-bg.png` → `drawable-nodpi/bili_topic_header.png`。
- `assets/fonts/digital_id_num.ttf` → `font/bili_digital_id.ttf`。
- `assets/fonts/custom_icon.ttf` → `font/bili_client_icons.ttf`；与早期 `piliplus_custom_icons.ttf` 独立保存，
  弹幕点赞、已点赞、复制、撤回、举报按本次字体对应的 `custom_icon.dart` 映射。

界面与数据参考：`lib/plugin/pl_player/view/widgets.dart` 的弹幕指向气泡、
`lib/pages/dynamics/widgets/author_panel.dart` 的装扮编号及 `lib/models/dynamics/result.dart` 的 fan 字段。
字体用于评论和动态装扮编号、空间头衔数字；图标和图片分别用于对应入口，不替换原生材质。
来源仓库及 GPL-3.0 许可证同上；这些资源沿用来源仓库的归档说明。
