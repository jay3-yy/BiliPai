# SharedView 系统兼容调查（2026-11-05）

## 目标与约束

目标是让官方 SDK 通过兼容接口连接系统已有的动画实现，而非把截图缩放或 Android 共享元素动画称为官方同款。当前范围限定普通 APK，不能依赖 Root、系统模块、系统签名或修改设备配置。本轮只读取设备文件、系统属性和公开 SDK 历史，未编译、安装或触发实验转场。

## 结论

这台 ROM **存在厂商的窗口卡片动画引擎**，不能把标准 SDK 返回 0 等同于整台设备没有动画能力。但目前找到的 `HyperViewScale` 入口还受系统服务端的应用允许名单控制，未找到可供 BiliPai 普通 APK 完整接入这条引擎的路径。

另有旧版系统圆角缩放接口，进入与返回链路均有实现。它与历史 SDK 适配器指向的 `HyperViewScale` 是不同路径，不能据此宣称已还原联盟动画。其普通应用调用条件、隐藏 API 可访问性和实际视觉效果尚未完整验证。

## 样本

- 型号：2211133C。
- 系统报告：Android 17 / API 37，`OS4.0.0.16.XPCCNXM`。
- 系统属性：`persist.sys.hyper_view_scale=true`。
- 调查使用的 SDK：`gsai.sdk:sharedview-kit:1.0.0.5`。
- 结论仅针对本样本及实际检查的产物，不代表全部系统或其他版本。

## 1. 当前 SDK 的标准入口

`CommonStubProtocolDelegate` 通过启动类加载器寻找：

- `com.androidex.animation.sharedview.ViewAnimationManager`
- `com.androidex.animation.sharedview.ViewAnimationListener`

1.0.0.5 还检查系统类与直接引用的类身份是否一致。包内同名 Manager 是占位桩，公开方法抛出 `UnsupportedOperationException("ROM implementation required")`，不是可移植的动画算法。

此前设备日志报告标准 Manager 不存在；本轮检查的 framework 类定义也未发现该门面。因此，修改支持标志、把同名桩放进 APK 或单纯改类加载器，均没有补上动画实现。

官方 Maven 元数据目前列出 1.0.0.3、1.0.0.4、1.0.0.5，最新为 1.0.0.5。本轮检查的 1.0.0.3、1.0.0.4 同样使用通用系统协议，未发现可通过切换这两个版本恢复的厂商适配分支。

## 2. 官方历史里的厂商桥接线索

官方示例历史提交 `205bcd02c1a0` 随附的 `SharedViewKit-debug.aar` 包含 `XiaomiAdapter`、`HonorAdapter`、`OppoAdapter`、`VivoAdapter`。

其中 `XiaomiAdapter`：

1. 查找 `android.app.hyperviewscale.HyperViewScaleRemoteStub`。
2. 调用 `getInstance()` 取得实际管理器。
3. 调用 `isFeatureEnable()` 查询能力。
4. 通过协议匹配器寻找 `makeViewAnimationOptions` 的 View/Bitmap 重载、取消、返回配置和扩展参数方法。

本机实际管理器存在，但本轮反编译结果里未找到这组协议方法；它提供的是更早的 `setScaleView(View, Context, Bundle, AnimationCallback)`。因此，历史调试 AAR 不是已验证的可直接替换版本，整包降级不能视为兼容已完成。

更早提交 `40d7df973a5f` 直接调用 `ActivityOptions.makeOneShotThumbnailAnimation`，随附的 `voneshotanimation` 也只是反射包装。本机 ActivityOptions 未找到这组方法；该历史分支没有提供跨设备的完整动画实现。

## 3. 本机真实 HyperViewScale 链路

实际实现位于：

- `/system/framework/framework.jar`：`HyperViewScaleRemoteStub`。
- `/system_ext/framework/miui-framework.hyperviewscale.jar`：`HyperViewScaleRemoteManager`、`HyperViewScaleRemoteTransition` 及动画实现。
- `/system/framework/services.jar`：`SafeActivityOptions`。
- `/system_ext/framework/miui-services.jar`：`HyperViewScaleTransitionImpl`。
- `/system_ext/framework/miui-services.hyperviewscale.jar`：`HyperViewScaleConfigManager`。

客户端管理器会读取卡片矩形、圆角、Bitmap 等参数，构造标识为 `view_scale_remote_transition` 的 RemoteTransition。`bundle_bitmap` 可以显式提供卡片像素，不必截取整个 Compose 根 View。

返回链路也有系统实现：服务端从目标 Activity 的 Intent 中读取 `view_scale_open` 对应的 RemoteTransition，并请求关闭转场。这说明该实现具有系统窗口进入和退出能力，而非只有单向缩略图动画。

### 系统端准入条件

`SafeActivityOptions.checkPermissions` 检查 RemoteTransition：

- 调用方拥有 `CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS`，或
- `HyperViewScaleTransitionImpl.isHyperViewScaleRemote` 判断通过。

后者要求转场标识匹配，**并且调用 UID 已在系统允许集合中**。仅设置转场名称不够。

`HyperViewScaleConfigManager.preBindApplication` 仅在应用包名命中系统规则时，把 UID 加入允许集合，并向 ApplicationInfo 注入配置。规则来自系统文件或系统云控，不是应用 Manifest 元数据。

本机可读取的系统规则文件 `/system_ext/etc/view_scale/view_scale_config.json` 包含官方 B 站、爱奇艺、拼多多、小红书、腾讯视频，没有 BiliPai 的正式包名或 debug 包名。云控缓存路径在当前 shell 查询中报告不存在；没有直接读取系统服务的内存集合，不能把静态规则检查等同于已执行过启动并观察到拒绝。

因此，根据当前源码链路和可读取配置，普通 APK 直接接入会面临系统端准入限制；本轮没有尝试启动来实测拒绝，也没有修改规则或系统属性。

## 4. 旧版圆角缩放接口：独立路径

本机 ActivityOptions 还提供：

- `makeScaleUpDown(...)`
- `makeScaleUpAnimationFromRoundedView(...)`

它们生成 102/103 类型的系统启动配置；`ActivityRecordImpl` 保存矩形、圆角、缩略图和来源关系，Shell 中的 `MiuiScaleUpDownController`、`ScaleUpDownTransition` 执行动画。

`Activity.updateScaleUpDownData(Bundle)` 的系统实现还支持更新返回位置、缩略图、颜色以及禁用返回动画。

该路径具有继续研究普通 APK 适配的价值，但不是历史联盟适配器所指向的 HyperViewScale 协议。未完整检查 Binder 入口权限与所有运行条件，也未证明它与联盟演示在实时画面、曲线、打断和预测返回上等价，当前不将其接入并标称为联盟同款。

## 后续可继续的功能工作

在普通 APK 的约束下，若仍要求连接上述 HyperViewScale 引擎，需要找到厂商提供的普通应用准入方式或更新后的通用系统协议入口。当前公开示例及本机已检查代码尚未提供这条路径。

若接受“调用 ROM 旧版原生圆角引擎”作为独立目标，可继续核实旧接口的调用条件，再设计进入、返回落点和卡片身份的兼容层；必须明确它与联盟协议的区别，不能静默替换目标。

应用现有 SDK、原生共享元素备用路径和其他未提交改动在本轮均保留。未加入未验证的 SDK 内部注入、系统权限绕过或实验启动分支。

## 资料与复查

- [官方 SharedViewDemo](https://gitee.com/itgsa_sharedviewkit/SharedViewDemo)
- [官方历史中的厂商适配 AAR](https://gitee.com/itgsa_sharedviewkit/SharedViewDemo/tree/205bcd02c1a0/app/libs)
- [统一 SDK 之前的系统直接调用示例](https://gitee.com/itgsa_sharedviewkit/SharedViewDemo/blob/40d7df973a5f/app/src/main/java/com/example/myapplication/presenter/HomePresenter.java)
- 本次原始设备 JAR、类定义检查结果及反编译分析文件保留在本机 `/tmp/bilipai-sharedview-rom-audit/`；这些临时文件可能被系统清理，不随仓库分发系统二进制或反编译源码。

关键样本 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| framework.jar | `629d083e6e169d6c08bf755c2abe8c40a1461fa72924a68fac4bf7a0abcdf58a` |
| services.jar | `d52371c7cef34466f3a7d4407686924acc94b228274cdee9dde0134210597e3c` |
| miui-services.jar | `7916103f8cdf288a1374174b79865516b1c7a6b911b13d63b5a41dbc2a87be68` |
| miui-framework.hyperviewscale.jar | `63e313183451a9405e6b30653454d22767ba9c82a7448fb3d9a885a28ed39f68` |
| miui-services.hyperviewscale.jar | `a86c2f5971cd6d0c3fc6ba83c723b5bf52866d740e3e39cb61a10e4957cee84e` |
| Miui-WindowManager-Shell.jar | `446d68f38e9a62270f295f7c292c423d3e7b23858a2ee1519ce0929a8185a19b` |

## 后续实现变更

用户随后选择恢复应用内动画并复刻运动行为。当时移除了 SDK 接入及跨 Activity 共享元素备用路径；随后按用户要求重新接入标准 SDK，满足协议与往返条件时官方优先，不满足时保留应用内动画。没有恢复共享元素备用路径，也没有接入本文调查的厂商私有接口。当前方案见 [应用内视频卡片开合](app-morph-motion-2026-11-05.md)。上述设备与 SDK 结论保留为调查时的证据，不描述当前应用运行依赖。

## 厂商圆角缩放路径探针实测（2026-11-05，同日晚间）

针对本文第 4 节的旧版圆角缩放接口，在样本设备上以最小探针 APK（targetSdk 37，普通签名，无特殊权限）做了运行时验证：

- `ActivityOptions.makeScaleUpDown`、`makeScaleUpAnimationFromRoundedView`、`Activity.updateScaleUpDownData` 三个隐藏方法在 targetSdk 37 下均可 `getDeclaredMethod` 并真实调用，未触发隐藏 API 拦截。
- 携带 `makeScaleUpDown`（类型 102）options 启动第二个 Activity 未被 `SafeActivityOptions` 拒绝；录屏确认系统引擎执行了打开缩放动画与返回收缩动画，exit/reenter 回调均触发。
- 返回落点刷新语义与联盟 SDK 相反：`ActivityRecordImpl` 在启动时把关联建在**来源** Activity record 上，`updateScaleUpDownData` 必须由来源 Activity 调用；目标页调用时服务端报告 `related: null` 并静默忽略。来源页调用后，返回收缩改向刷新后的屏幕坐标。
- `scaleDownColor` 是卡片位图下层的 mask 填充色，位图与落点矩形尺寸一致时不可见；背景层底色由系统按夜间模式自取。

## 当前接入顺序与边界

应用按两条路径择一：金标联盟 SDK（协议与往返条件满足时）→ 应用内形变。曾接入的厂商圆角缩放路径（反射探测旧版 102/103 接口）在样本设备实测后移除：其返回段落位正确，但打开动画的可见内容是目标窗口表面本身，详情首帧为深色骨架时表现为深色卡片缩放（深色目标探针与 BiliPai 实测一致），与卡片展开的观感不符；窗口首帧种子（`LocalVideoDetailOpeningSeed`，打开时捕获的卡片像素同步垫入封面容器底层）保留并服务官方窗口路径。

嵌套场景共用同一调度：独立视频窗口宿主同一个 `AppNavigation`（`hostActivity` 解析为该窗口自身），相关推荐点击在窗口内同样经 `canAttempt → tryOpen` 打开官方子窗口，子窗口按令牌独立完成返回刷新与首帧种子，父窗口相关推荐锚点在返回时重新读取 live 几何；宿主动效设置变化经 `updateOfficialReturnMotion` 推送给窗口刷新返回配置。

## 自研动画与联盟引擎的参数对齐审计（2026-11-06）

以样本设备系统引擎反编译常量（`HyperViewScaleAnimationImpl` 的 Folme spring 表）为权威逐项核对 `VideoCardMorphSprings`：

| 通道 | 引擎打开 | 引擎关闭 | 自研打开 | 自研关闭 |
| --- | --- | --- | --- | --- |
| 位置 | 1.1 / 0.229 | 0.95 / 0.29 | 一致 | 一致 |
| 尺寸 | 1.1 / 0.268 | 0.95 / 0.29 | 一致 | 一致 |
| 圆角 | 1.0 / 0.18 | 1.0 / 0.20 | 一致（终点为窗口圆角） | 一致 |
| 模糊半径 | 1.0 / 0.25 | 1.0 / 0.15 | 一致（effects 通道） | 一致 |
| 长宽比 | 1.1 / 0.268（=尺寸） | **1.0 / 0.29** | 由尺寸通道派生 | 由尺寸通道派生 |
| 遮罩透明度 | 0.95 / 0.12 | 0.95 / 0.18 | 一致（mask 通道） | 一致 |

- 打开段引擎的 RATIO 与 SIZE 参数相同，自研的均匀尺寸模型与之等价；关闭段引擎的 RATIO 阻尼为 1.0（尺寸 0.95），自研长宽比随尺寸通道走，差异为关闭中段形变弹性的百分之几，时长 0.29s，低于感知阈值。
- 引擎的 RATIO 通道驱动的是 leash 内部内容矩形的宽高比（`updateRect` 的 innerDimensions）；自研飞壳用 FillWidthTop/CropCenter 内容补偿实现同一目标（内容锚定封面位置），不设独立 ratio 通道。
- 遮罩已拆为独立 mask 通道（`VideoCardMorphSprings.openMask/closeMask` → 时钟 `maskProgress()` → Host 层与来源页 scrim），压暗先于模糊到位；无 Miuix 导航驱动的回灌路径（shared morph / fallback）退化为跟随景深单通道，与旧行为一致。
- 背景缩放已移除：联盟协议的 `EXTRA_BACKGROUND_SCALE` 默认 1.0（不缩放），HyperViewScale 引擎对背景只做遮罩暗化与模糊；背景缩放（缩至 0.9）是小米旧 102/103 引擎的行为。被覆盖页 3% 缩放与覆盖圆角随之从视频开合路径移除（设置容器转场沿用旧层次感，不在对齐范围）。
- 圆角终点已从归零改为窗口圆角（`deviceCornerDp`），与引擎 leashCorner 的起止语义一致；落位帧整页铺满屏幕后由硬件圆角接管，无跳变。
- 其余差异来自实现载体：官方为系统窗口合成（引擎内实时内容），自研为应用内绘制复刻；不能凭参数相同宣称视觉、性能或手感一比一。

## 联盟模糊的接入方式与自研路径的性能优化（2026-11-06）

联盟模糊全部发生在系统合成侧：应用只写 `EXTRA_BLUR` 布尔（需设备声明），引擎在卡片 leash 背后建独立 blur leash，每帧 `setBackgroundBlurRadius + setBlurScaleRatio`（HyperViewScaleAnimationImpl.updateBlurAnim），由 `BLUR_RADIUS`（1.0/0.25 开、1.0/0.15 关）与 `BLUR_ALPHA`（1.0/0.18 开、1.0/0.15 关）三条 Folme 通道驱动；旧 102 引擎同思路（`setSelfBlurRadius` 0↔40px）。模糊输入即窗口表面，不经过应用渲染管线。

自研路径的等价降本（应用内 `RenderEffect` 无法进入合成器，改为减少重光栅化次数）：冻结层模糊半径**恒定取峰值**（`blurRadiusPxOverride`），景深只驱动 `layerAlpha` 与锐利内容合成混合（`frame.blurAlpha`，即引擎 BLUR_ALPHA 语义）；冻结内容静态下 RenderThread 对固定半径只光栅化一次，开合全程的逐帧变半径重模糊（每向约 7~16 次）降为一次。设置容器 covered 分支与共享快照路径下方无锐利内容可混合，保留渐进半径方案不变；回灌/fallback 路径 `maskProgress`/`blurAlpha` 退化为跟随景深，与旧行为一致。
- 探针产物与录屏保留在 `/tmp/bilipai-sharedview-probe/`（probe-run.mp4、probe-run2.mp4），不随仓库分发。
