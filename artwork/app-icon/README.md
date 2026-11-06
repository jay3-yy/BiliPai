# 启动图标源文件与导出

## 当前定稿

- 弹幕播放窗：保留左倾构图、深色描边和朝右播放键，去掉下方灰色投影；采用暖白 `#F3EEE6` 背景与墨灰图案，深色背景为 `#242321`。
- 蓝雪女仆、蓝雪女仆·正面：恢复原头像构图，只移除素材自带的圆形底图和圆框。不采用后续补身体、补手、改变配色的方案；侧倾闭嘴微笑与正面张嘴微笑保留各自区别。沿用白色/深色背景。两款彩色头像在原构图基础上统一上移 2dp，缩放比例调整为原构图的 88%（比上一版再缩小约 9%），原画内容不变。
- 蓝雪女仆·喇叭：本次不修改。

## 素材

- `bilipai-selected-original.png`：弹幕窗最初的蓝色参考稿。
- `bilipai-play-window-neutral-foreground.png`：当前无蓝色、无投影前景。
- `bilipai-play-window-monochrome.svg`：对应主题单色矢量。
- `blue_snow_maid_frameless_foreground.png`、`blue_snow_maid_front_frameless_foreground.png`：第一次去圆框的头像前景；当前资源只使用这两份人物素材。
- `cleanup-2026-11-06-prompts.json`：去投影、去圆框的图像编辑记录。
- 既有 `*4096.png`、`*_native.png`：原头像及历史制作来源，保留作品牌身份参考。

## Android 接入

按 [Android 官方自适应图标说明](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) 分开提供背景、前景和主题单色层，图层均为 108dp。前景使用真正透明的 PNG，不预先绘制桌面外层遮罩。圆形、圆角方形、方圆形等外形由桌面决定；移除素材圆框不意味着强制桌面使用方形。

弹幕窗主体控制在中央 66dp 安全区内。人物保持近景头像比例，只把脸部、头饰和主要蝴蝶结放在安全区，原有长发及肩部属于可由遮罩裁切的外延内容；不再为保护整个半身而把人物缩成小贴纸。关键区域坐标见 `export_frameless_maids.py`，检查结果分别记录关键区域和装饰外沿的裁切量，不能把外沿正常裁切当作关键内容丢失。

保留 `icon_3d` 等设置键、Activity alias 和资源入口。原「3D立体」显示名为「弹幕播放窗」，已有选择无需迁移。女仆系列与弹幕播放窗共用图标页顶部的“跟随系统／浅色／深色”入口；弹幕窗的固定浅深色拥有独立桌面 alias、图标资源、预览与启动页入口，关闭启动图标时也沿用所选外观。普通、圆形后备图、显式浅深色和跟随系统的资源均同步导出；去重别名及 SHA 清单一起更新。播放通知小图标使用相应单色资源。人物头像的启动页、设置预览与默认商店图同步引用或导出当前造型。

## 导出与复查

使用 Pillow，执行以下脚本即可更新资源，不调用 Gradle：

```sh
PYTHONDONTWRITEBYTECODE=1 python artwork/app-icon/export_play_window.py
PYTHONDONTWRITEBYTECODE=1 python artwork/app-icon/export_frameless_maids.py
python scripts/deduplicate_launcher_images.py --check
```

前景尺寸为 108/162/216/324/432px，普通与圆形后备尺寸为 48/72/96/144/192px。主题人物字形沿用已有的简化单色图案，并重新检查安全区；不会把复杂彩色头像直接压成实心块。

- `launcher-final-comparison.png`：当前三个图标的浅深色对照。
- `play-window-previews/`：弹幕窗遮罩、主题、180/60/32px 及几何记录。
- `frameless-previews/`：两款头像的遮罩、主题、180/60/32px 及关键内容裁切记录。

已完成静态资源、别名、透明度、关键安全区和示意遮罩检查。人物在 32px 下保留头像轮廓、发色和表情方向，细小蕾丝不作为可读性承诺。预览的主题颜色仅模拟系统着色，不是固定配色。

未编译、未运行 Gradle 测试，实际桌面缓存刷新及系统启动动画尚未验证。
