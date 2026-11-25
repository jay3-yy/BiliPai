globalThis.BPX = {
  manifest: {
    bpxVersion: 1,
    id: "community.comment.timestamp_rep",
    name: "评论指路课代表置顶",
    version: "1.0.0",
    description: "提取热评中的时间轴指路生成置顶跳转卡，并折叠抽奖刷屏",
    license: "MIT",
    licenseNote: "MIT 开源社区净化脚本",
    author: {
      name: "省流协会",
      biliMid: 778899,
      github: "jay3-yy"
    },
    mounts: [
      { slot: "community.craft", id: "rep_pin", title: "评论指路提炼", entry: "enhanceComments" }
    ],
    permissions: {
      videoContext: ["comments"]
    }
  },
  handlers: {
    enhanceComments(snapshot) {
      const anchors = [];
      const foldRpids = [];
      const timeRe = /(?:^|\s)(\d{1,2}):(\d{2})\s*([^\n]{2,24})/g;

      for (const c of snapshot.comments || []) {
        const text = String(c.content || "");
        if (/^(转发抽奖|拉低中奖率|蹲一个中奖)/.test(text.trim())) {
          foldRpids.push(c.rpid);
          continue;
        }
        let m;
        while ((m = timeRe.exec(text)) !== null && anchors.length < 6) {
          const sec = Number(m[1]) * 60 + Number(m[2]);
          anchors.push({ timeSec: sec, label: m[3].trim() });
        }
      }
      return {
        pinnedCard: anchors.length
          ? { title: "热评指路速览", timestamps: anchors }
          : undefined,
        foldRpids
      };
    }
  }
};
