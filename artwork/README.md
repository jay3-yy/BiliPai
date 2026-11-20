# 设计资源与归档

- [启动图标导出](app-icon/README.md)使用目录内的当前前景、单色图层与矢量资源。历史彩色 4K 母版、旧尺寸头像、蓝色弹幕窗参考稿与概念图可在[归档提交](https://github.com/jay3-yy/BiliPai/tree/90ecb0e4965a1d105379a99c2165c6e0cab36c4e/artwork/app-icon)查阅。
- 品牌动效原画用于生成应用资源。离线预览及抽样帧可以按需生成，旧版输出见[归档提交](https://github.com/jay3-yy/BiliPai/tree/90ecb0e4965a1d105379a99c2165c6e0cab36c4e/artwork/brand-motion)。

在仓库根目录用 Python 和 Pillow 生成离线预览：

```sh
python3 artwork/brand-motion/package_assets.py --preview-only
python3 artwork/brand-motion/validate.py
```

生成后打开 `artwork/brand-motion/preview.html`。用 `python3 artwork/brand-motion/render-cleaning-frames.py --state retry` 可生成重试动作的抽样帧。

## 蓝雪女仆示例皮肤

从仓库根目录进入皮肤示例目录，再解包资源。已有本地图片会保留。

```sh
cd plugins/samples/blue-snow-maid-skin
unzip -n blue-snow-maid.bpskin 'assets/*.png'
```

在 `assets/` 中编辑 PNG，或调整 `skin-manifest.json`。打包任务 `../../../gradlew -p . packageBpSkin` 优先使用本地图片，其余图片从已发布的皮肤包读取。新包输出到 `build/distributions/blue-snow-maid.bpskin`。确认新包后，用它更新仓库里的 `blue-snow-maid.bpskin`。如果改了顶部背景，同时更新已发布版本会直接下载的 `assets/top_atmosphere.png`。
