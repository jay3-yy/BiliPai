# 零 Git 门槛投稿与自建订阅源

好点子不该被命令行挡在门外。BiliPai 采用 **「官方精选仓表单直投 + 自由自定义订阅源」** 双轨并行机制——无论你会不会用 Git，都能让作品被看见，并在 App 内点亮你的专属创作者名片。

---

## 1. 官方精选源「三条明文准入红线」

向官方精选库投稿前，请确认作品符合以下三条铁律（审稿机器人与维护者均按此标准透明核验）：

1. **内容合规红线**：严禁任何绕过 B 站大会员/付费鉴权、破解版权限制，或内置盗版影视直链的扩展。
2. **素材版权红线**：包含视觉素材的装扮扩展必须填写 `licenseNote`，仅接收原创绘制、CC 开源协议或拥有明确可再分发授权的作品，严禁直接解包搬运商业付费装扮。
3. **透明沙盒红线**：所有 `.bpx.js` 脚本必须为**未混淆、未压缩的明文源码**；凡发起网络请求者，`permissions.networkDomains` 必须精确填写具体域名（禁止 `*` 通配符），接受社区公开审计。

---

## 2. 不会 Git？30 秒填写图形化表单投稿

1. 先在 [**在线校验扫码台**](/playground) 粘贴你的代码，确认体检结果为 **「全绿通过」** 且在手机扫码实测正常。
2. 打开 GitHub 仓库的 [**🧩 投稿 BiliPai 社区扩展（图形化表单）**](https://github.com/jay3-yy/BiliPai/issues/new?template=submit-extension.yml)。
3. 按表单填入你的扩展名称、一句话简介、**你的 B 站 UID**（可选，用于在 App 内创作者名片生成一键直达你 B 站空间的按钮），并粘贴 `.bpx` 源码。
4. 点击提交后，**BiliPai Review Bot** 会在数十秒内自动完成 Schema、域名白名单、禁混淆与正则安全体检，自动生成预览二维码评论并创建 Pull Request；维护者点击 Merge 后即自动签名上架！

---

## 3. App 内「创作者名片」正反馈

只要你在 `manifest.author` 中填写了信息：

```json
"author": {
  "name": "你的名字",
  "biliMid": 12345678,
  "github": "yourname",
  "bio": "专注 B 站知识区效率工具"
}
```

每一位安装该扩展的用户，都会在 **安装核验单（Trust Sheet）** 与 **扩展详情页** 顶部看到由三材质渲染的创作者名片，可一键跳转关注你的 B 站空间或为你点亮感谢。欢迎把工坊生成的安装二维码直接晒到你的 B 站动态、专栏或酷安帖子中！

---

## 4. 进阶：自建独立订阅源（`registry.json`）

如果你维护的是个人聚合频道、高频实验脚本或小众定制集合，无需等待官方合入——只需在任意静态托管（GitHub Pages / Gist / Cloudflare Pages）放置一份 `registry.json`，用户添加一次订阅链接即可持续收到版本更新红点：

```json
{
  "registryVersion": 1,
  "name": "某某的 BiliPai 扩展实验室",
  "maintainer": { "name": "某某", "biliMid": 12345678 },
  "updatedAt": "2026-11-25T12:00:00Z",
  "extensions": [
    {
      "id": "lab.example.my_ext",
      "name": "示例扩展",
      "version": "1.0.2",
      "minAppVersionCode": 612,
      "slots": ["detail.companion"],
      "description": "一句话说明核心能力",
      "downloadUrl": "https://example.github.io/bpx-repo/my_ext.bpx.js",
      "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "changelog": "优化长视频字幕分段策略"
    }
  ]
}
```
