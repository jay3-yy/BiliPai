globalThis.BPX = {
  manifest: {
    bpxVersion: 1,
    id: "community.danmaku.dandan_supplement",
    name: "弹弹Play 弹幕补全",
    version: "1.0.0",
    description: "为番剧与冷门剧集自动匹配补充弹幕轨并与主站弹幕混流",
    license: "MIT",
    licenseNote: "仅调用公开弹幕匹配接口，MIT 协议开源",
    author: {
      name: "番剧补完计划",
      biliMid: 668899,
      github: "jay3-yy"
    },
    mounts: [
      { slot: "player.track", id: "dandan_track", title: "外挂弹幕补全", entry: "loadTracks" }
    ],
    permissions: {
      videoContext: ["metadata"],
      networkDomains: ["api.dandanplay.net"]
    },
    settings: [
      { key: "maxCount", type: "slider", title: "最大补充条数", default: 800, min: 100, max: 2000, step: 100, unit: "条" }
    ]
  },
  handlers: {
    async loadTracks(ctx, settings) {
      const matchRes = await BiliPai.http.post(
        "https://api.dandanplay.net/api/v2/match",
        JSON.stringify({ fileName: ctx.title, fileDuration: ctx.durationSec, matchMode: "fileNameOnly" }),
        { "Content-Type": "application/json", Accept: "application/json" }
      );
      if (matchRes.status !== 200) return { danmaku: [] };
      const matched = JSON.parse(matchRes.body).matches?.[0];
      if (!matched?.episodeId) return { danmaku: [] };

      const commentRes = await BiliPai.http.get(
        "https://api.dandanplay.net/api/v2/comment/" + matched.episodeId + "?withRelated=true"
      );
      if (commentRes.status !== 200) return { danmaku: [] };
      const comments = JSON.parse(commentRes.body).comments || [];
      const limit = Number(settings.maxCount || 800);
      const danmaku = comments.slice(0, limit).map((item) => {
        const parts = String(item.p || "0,1,16777215").split(",");
        const modeNum = Number(parts[1]);
        return {
          timeMs: Math.round(Number(parts[0]) * 1000),
          text: String(item.m || ""),
          mode: modeNum === 5 ? "top" : modeNum === 4 ? "bottom" : "scroll",
          color: "#" + Number(parts[2] || 16777215).toString(16).padStart(6, "0")
        };
      });
      return { danmaku };
    }
  }
};
