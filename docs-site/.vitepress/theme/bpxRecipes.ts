export type BpxSlotId =
  | 'feed.pipe'
  | 'detail.companion'
  | 'player.track'
  | 'home.lens'
  | 'community.craft'
  | 'theme.skin'

export interface BpxRecipe {
  id: string
  title: string
  slot: BpxSlotId
  slotLabel: string
  mountPreview: string
  format: 'json' | 'js'
  lines: number
  summary: string
  highlights: string[]
  docLink: string
  code: string
}

export const SLOT_META: Record<
  BpxSlotId,
  { label: string; mountLocation: string; contractHint: string }
> = {
  'feed.pipe': {
    label: '推荐流净化与角标 (feed.pipe)',
    mountLocation: '首页推荐 / 热门 / 搜索卡片管线',
    contractHint:
      '可使用纯 JSON 声明 rules（动作支持 hide / fold / badge / rewriteTitle），或编写批处理函数 transformFeed(cards, settings)（预算 ≤ 16ms）。'
  },
  'detail.companion': {
    label: '详情页 AI 与伴侣面板 (detail.companion)',
    mountLocation: '视频简介下方「扩展胶囊」→ 同窗半屏玻璃面板',
    contractHint:
      '导出 async onOpenSheet(ctx, settings, emit)，支持 emit 流式输出 markdown / timestamps（点击直跳秒数）/ kv / actions 结构块。'
  },
  'player.track': {
    label: '播放器时间轴与外挂轨 (player.track)',
    mountLocation: '播放器进度条区间层 / 弹幕混流层 / 字幕菜单',
    contractHint:
      '导出 async loadTracks(ctx, settings)，返回 { segments?, danmaku?, subtitles? } 纯数据轨道，不直接操作播放器实例。'
  },
  'home.lens': {
    label: '首页算法透镜与频道 (home.lens)',
    mountLocation: '首页顶栏透镜切换器 / 可固定平级频道 Tab',
    contractHint:
      'mode="rerank" 导出 rerankCandidates(req, settings) 返回重排分数与推荐签；mode="channel" 导出 loadChannelFeed(query, settings)。'
  },
  'community.craft': {
    label: '评论提炼与弹幕词条 (community.craft)',
    mountLocation: '评论区顶部置顶摘要卡 / 刷屏折叠层',
    contractHint:
      '导出 enhanceComments(snapshot, settings)，返回 { pinnedCard?: { title, timestamps?, summary? }, foldRpids?: number[] }。'
  },
  'theme.skin': {
    label: '三材质光学主题装扮 (theme.skin)',
    mountLocation: '首页顶底栏 / 液态玻璃折射染色 / 进度条游标',
    contractHint:
      '纯 JSON 声明 skin.tokens.{light, dark, liquidGlass}，其中 liquidGlass.tintOpacity 须在 0.08..0.45 之间以保留实时折射采样，且必填 licenseNote。'
  }
}

export const STARTER_RECIPES: BpxRecipe[] = [
  {
    id: 'declickbait',
    title: '去标题党与热点刷屏折叠',
    slot: 'feed.pipe',
    slotLabel: 'FeedPipe · 规则快路径',
    mountPreview: '首页推荐卡片标题与右上角毛玻璃角标',
    format: 'json',
    lines: 34,
    summary: '正则剥离「震惊！」「家人们谁懂」等夸张前缀，温和折叠低赞超短视频，并为 30 分钟以上长视频打上「深度长片」角标。',
    highlights: ['Kotlin 原生快路径 0ms 级开销', '支持一键改词扫码直装'],
    docLink: '/cookbook/declickbait',
    code: `{
  "bpxVersion": 1,
  "id": "community.clean.declickbait",
  "name": "去标题党与热点折叠",
  "version": "1.0.0",
  "description": "剥离夸张震惊体前缀，折叠超短引流视频并为深度长片打标",
  "license": "MIT",
  "licenseNote": "MIT 开源示例规则，允许自由修改与二次分发",
  "author": {
    "name": "BiliPai 共创工坊",
    "biliMid": 233333,
    "github": "jay3-yy",
    "bio": "让时间流回到清爽可预期"
  },
  "mounts": [
    { "slot": "feed.pipe", "id": "clean_feed", "title": "推荐流去标题党管线" }
  ],
  "rules": [
    {
      "condition": { "field": "title", "op": "regex", "value": "^(震惊[！!]|千万别[^，。！？]{1,8}[！!]|家人们谁懂啊[！!，,]?)" },
      "action": "rewriteTitle",
      "pattern": "^(震惊[！!]|千万别[^，。！？]{1,8}[！!]|家人们谁懂啊[！!，,]?)\\\\s*",
      "replacement": ""
    },
    {
      "condition": {
        "and": [
          { "field": "durationSec", "op": "lt", "value": 25 },
          { "field": "stat.like", "op": "lt", "value": 500 }
        ]
      },
      "action": "fold",
      "groupLabel": "已折叠低赞超短视频"
    },
    {
      "condition": { "field": "durationSec", "op": "ge", "value": 1800 },
      "action": "badge",
      "text": "深度长片",
      "tone": "accent"
    }
  ]
}`
  },
  {
    id: 'ai-summary',
    title: '视频页 AI 省流课代表',
    slot: 'detail.companion',
    slotLabel: 'DetailSheet · 流式玻璃面板',
    mountPreview: '视频简介下方「省流课代表」胶囊 → 半屏玻璃弹窗',
    format: 'js',
    lines: 62,
    summary: '读取当前视频 CC 字幕，通过用户自配的 DeepSeek 接口流式输出核心要点，点击 [秒数] 芯片直接跳转播放器对应画面。',
    highlights: ['BYOK 自带密钥本地加密保存', 'SSE 逐字流式渲染 + 时间戳直跳'],
    docLink: '/cookbook/ai-summary',
    code: `globalThis.BPX = {
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
        return { blocks: [{ type: "markdown", content: "当前视频无 CC 字幕，简介摘要：\\n\\n" + (ctx.desc || "无") }] };
      }
      const lines = ctx.subtitles.slice(0, 260).map((s) => "[" + Math.floor(s.fromSec) + "s] " + s.content).join("\\n");
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
              { role: "user", content: ctx.title + "\\n" + lines }
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
      const re = /\\[(\\d+)s\\]\\s*([^\\n]+)/g;
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
};`
  },
  {
    id: 'external-danmaku',
    title: '弹弹Play 外挂弹幕混流',
    slot: 'player.track',
    slotLabel: 'PlayerTrack · 外挂轨道混流',
    mountPreview: '播放器弹幕层 & 更多菜单轨道开关',
    format: 'js',
    lines: 48,
    summary: '观看港澳台番剧或冷门生肉时，按视频标题与时长自动匹配第三方公开弹幕池，与原生弹幕轨无缝合流渲染。',
    highlights: ['精确域名白名单隔离', '不触碰 ExoPlayer 线程安全'],
    docLink: '/cookbook/external-danmaku',
    code: `globalThis.BPX = {
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
};`
  },
  {
    id: 'hidden-gems-lens',
    title: '「冷门宝藏」推荐流透镜',
    slot: 'home.lens',
    slotLabel: 'HomeLens · 算法重排透镜',
    mountPreview: '首页顶栏「透镜切换器」与推荐理由小标签',
    format: 'js',
    lines: 44,
    summary: '打破信息茧房：自动压低超大体量常看号权重，挖掘点赞/播放比极高的中小 UP 主精品长视频并附上推荐理由。',
    highlights: ['0 网络请求纯本地计算', '顶栏一键切回默认推荐'],
    docLink: '/cookbook/hidden-gems-lens',
    code: `globalThis.BPX = {
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
};`
  },
  {
    id: 'comment-rep',
    title: '评论区「指路课代表」置顶卡',
    slot: 'community.craft',
    slotLabel: 'CommunityCraft · 社区精炼',
    mountPreview: '评论区首屏顶栏「指路速览卡」+ 刷屏折叠',
    format: 'js',
    lines: 43,
    summary: '自动识别热评中的时间轴指路（如 02:15 正片开始）并提炼为评论顶栏可跳转卡片，同时温和折叠重复刷屏的抽奖评论。',
    highlights: ['评论指路秒变可点章节', '纯本地毫秒级扫描'],
    docLink: '/cookbook/comment-rep',
    code: `globalThis.BPX = {
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
      const timeRe = /(?:^|\\s)(\\d{1,2}):(\\d{2})\\s*([^\\n]{2,24})/g;

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
};`
  },
  {
    id: 'three-material-skin',
    title: '三材质光学主题装扮（霜樱玻璃）',
    slot: 'theme.skin',
    slotLabel: 'ThemeSkin · 三材质自适应',
    mountPreview: '首页顶底栏氛围 + 液态玻璃折射染色 + 进度条游标',
    format: 'json',
    lines: 40,
    summary: '严格遵循 MD3 / Miuix / 液态玻璃正交分工：亮暗双色语义适配，开启液态玻璃时自动退让为 0.22 透光折射染色。',
    highlights: ['0 代码纯数据安全包', '液态玻璃实时折射不糊底'],
    docLink: '/cookbook/three-material-skin',
    code: `{
  "bpxVersion": 1,
  "id": "community.skin.frost_sakura",
  "name": "霜樱光学主题",
  "version": "1.0.0",
  "description": "适配亮暗双模与液态玻璃实时折射染色的轻量樱粉装扮色板",
  "license": "CC-BY-4.0",
  "licenseNote": "原创配色方案，采用 CC-BY-4.0 协议允许社区自由分享与改编",
  "author": {
    "name": "蓝雪视觉工坊",
    "biliMid": 888888,
    "github": "jay3-yy",
    "bio": "保持三材质通透呼吸感"
  },
  "mounts": [
    { "slot": "theme.skin", "id": "frost_sakura_skin", "title": "霜樱光学染色" }
  ],
  "skin": {
    "surfaces": ["HOME_TOP_CHROME", "HOME_BOTTOM_BAR", "PLAYER_PROGRESS"],
    "tokens": {
      "light": {
        "topAtmosphereTint": "#FDF2F7",
        "bottomBarTrimTint": "#FCE8F1",
        "bottomBarSelectedTint": "#D94E86",
        "seekbarAccent": "#E05288"
      },
      "dark": {
        "topAtmosphereTint": "#241920",
        "bottomBarTrimTint": "#2A1C25",
        "bottomBarSelectedTint": "#FF82B0",
        "seekbarAccent": "#FF7AA8"
      },
      "liquidGlass": {
        "surfaceTint": "#F7BFD6",
        "refractionAccent": "#FF8AB5",
        "tintOpacity": 0.22
      }
    }
  }
}`
  }
]

export function buildCapsuleAiPrompt(slot: BpxSlotId, userGoal: string): string {
  const meta = SLOT_META[slot]
  const sample = STARTER_RECIPES.find((r) => r.slot === slot) || STARTER_RECIPES[0]
  const goalText =
    userGoal.trim() || '请在下方样板基础上帮我设计一个实用、简洁且符合规范的扩展'

  return `你现在是 BiliPai 扩展系统 2.0 (.bpx v1) 专家开发者。请根据我的需求，输出一份**可直接粘贴进 BiliPai 在线校验台**的单文件扩展代码（不要省略任何字段，不要输出多余解释）。

## 我的创作需求
- 目标挂载插槽：${meta.label}（挂载位置：${meta.mountLocation}）
- 具体功能想法：${goalText}

## 必须严格遵守的硬性契约（违反任意一条将被校验器拦截）
1. 输出格式：纯规则或主题装扮输出单个 JSON 代码块（.bpx.json）；含动态逻辑输出单个 JS 代码块（使用 \`globalThis.BPX = { manifest: {...}, handlers: {...} }\`）。
2. 清单字段：\`bpxVersion\` 固定为 \`1\`；\`id\` 必须匹配 \`^[a-z0-9]+(\\.[a-z0-9_]+){1,4}$\`；\`version\` 为语义化版本（如 \`1.0.0\`）；包含 \`author: { name, biliMid }\` 与 \`licenseNote\`。
3. 严禁浏览器全局对象：代码运行在独立 QuickJS 沙盒，**禁止使用 \`window\`、\`document\`、\`fetch\`、\`XMLHttpRequest\`、\`localStorage\`、\`eval\`**。所有网络/解析/存储必须调用 \`BiliPai.http.{get,post,streamSse}\`、\`BiliPai.ws\`、\`BiliPai.html.parse\`、\`BiliPai.crypto\`、\`BiliPai.storage\`。
4. 精确域名白名单：代码中访问的每一个外部域名，必须精确写入 \`manifest.permissions.networkDomains\`（禁止 \`*\` 通配符）。
5. 宿主垄断 UI：禁止返回 HTML 界面，必须按插槽契约返回结构化数据块（宿主会自动按 Material 3 / Miuix / 液态玻璃三材质渲染）。
6. 插槽专属提示：${meta.contractHint}

## 该插槽官方标准参考样板
\`\`\`${sample.format}
${sample.code}
\`\`\`

请直接给出完整可用的代码：`
}
