globalThis.BPX = {
  manifest: {
    bpxVersion: 1,
    id: "community.ai.class_rep",
    name: "AI 省流课代表",
    version: "1.0.0",
    description: "提取 CC 字幕流式生成章节摘要与可点击跳转时间轴",
    license: "MIT",
    licenseNote: "MIT 开源脚本，用户自备大模型 API Key",
    author: {
      name: "BiliPai Lab",
      biliMid: 10001,
      github: "jay3-yy",
      bio: "把半小时长视频浓缩成 30 秒可跳转提纲"
    },
    mounts: [
      { slot: "detail.companion", id: "summary", title: "省流课代表", entry: "onOpenSheet" }
    ],
    permissions: {
      videoContext: ["metadata", "subtitles"],
      networkDomains: ["api.deepseek.com"]
    },
    settings: [
      { key: "apiKey", type: "secret", title: "DeepSeek API Key", required: true },
      {
        key: "style",
        type: "segment",
        title: "摘要详略",
        default: "chapters",
        options: [
          { label: "三句话省流", value: "brief" },
          { label: "章节时间轴", value: "chapters" }
        ]
      }
    ]
  },
  handlers: {
    async onOpenSheet(ctx, settings, emit) {
      if (!settings.apiKey) throw new Error("请先在扩展设置中填写 DeepSeek API Key");
      if (!ctx.subtitles || ctx.subtitles.length === 0) {
        return { blocks: [{ type: "markdown", content: "当前视频无 CC 字幕，简介摘要：\n\n" + (ctx.desc || "无") }] };
      }
      const lines = ctx.subtitles.slice(0, 260).map((s) => "[" + Math.floor(s.fromSec) + "s] " + s.content).join("\n");
      let text = "";
      await BiliPai.http.streamSse(
        "https://api.deepseek.com/chat/completions",
        {
          method: "POST",
          headers: { Authorization: "Bearer " + settings.apiKey, "Content-Type": "application/json" },
          body: JSON.stringify({
            model: "deepseek-chat",
            stream: true,
            messages: [
              { role: "system", content: "提炼B站视频核心观点，关键结论前标注 [秒数s]。" },
              { role: "user", content: ctx.title + "\n" + lines }
            ]
          })
        },
        (ev) => {
          if (ev.data === "[DONE]") return;
          const delta = JSON.parse(ev.data).choices?.[0]?.delta?.content || "";
          if (delta) {
            text += delta;
            emit({ blocks: [{ type: "markdown", content: text }] });
          }
        }
      );
      const items = [];
      const re = /\[(\d+)s\]\s*([^\n]+)/g;
      let m;
      while ((m = re.exec(text)) !== null && items.length < 8) {
        items.push({ timeSec: Number(m[1]), label: m[2].slice(0, 26) });
      }
      return {
        blocks: [
          ...(items.length ? [{ type: "timestamps", items }] : []),
          { type: "markdown", content: text },
          { type: "actions", buttons: [{ id: "copy", label: "复制提纲", copyText: text }] }
        ]
      };
    }
  }
};
