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
