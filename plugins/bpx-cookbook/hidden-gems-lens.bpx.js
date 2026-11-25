globalThis.BPX = {
  manifest: {
    bpxVersion: 1,
    id: "community.lens.hidden_gems",
    name: "冷门宝藏挖掘透镜",
    version: "1.0.0",
    description: "按高赞播比与完播潜力重排候选流，优先发现中小 UP 主硬核作品",
    license: "MIT",
    licenseNote: "纯本地重排算法，不上传任何观看记录",
    author: {
      name: "算法驯服师",
      biliMid: 5201314,
      github: "jay3-yy"
    },
    mounts: [
      { slot: "home.lens", id: "gems", title: "冷门宝藏", mode: "rerank", entry: "rerankCandidates" }
    ],
    permissions: {
      videoContext: ["candidates", "historySummary"]
    },
    settings: [
      { key: "maxPlayCount", type: "slider", title: "播放量上限偏好", default: 200000, min: 20000, max: 1000000, step: 20000, unit: "次" }
    ]
  },
  handlers: {
    rerankCandidates(req, settings) {
      const cap = Number(settings.maxPlayCount || 200000);
      const seen = new Set(req.consumedBvids || []);
      return (req.candidates || [])
        .filter((v) => !seen.has(v.bvid) && v.durationSec >= 120)
        .map((v) => {
          const plays = Math.max(v.playCount || 1, 1);
          const likeRatio = (v.likeCount || 0) / plays;
          const underCapBonus = plays <= cap ? 1.35 : 0.75;
          const depthBonus = v.durationSec >= 480 ? 1.2 : 1.0;
          const score = likeRatio * 1000 * underCapBonus * depthBonus;
          return {
            bvid: v.bvid,
            score: Math.round(score * 100) / 100,
            reasonBadge: likeRatio >= 0.08 ? "高赞播比宝藏" : "潜力深读"
          };
        })
        .sort((a, b) => b.score - a.score);
    }
  }
};
