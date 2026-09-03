# 更新日志（CHANGELOG）

> LuzzyRP 遵循语义化版本（`MAJOR.MINOR.PATCH`）；`x.y.0` 视为稳定版并附 APK。
> 格式：`### vX.Y.Z — 标题` + 「新增 / 优化 / 修复 / 注意事项」分类要点 + 构建结果与 versionCode。
> **v1.0.0 起：每条记录注明上游基线版本（RP-Hub）。** 旧 v0.x 记录保留于下方历史区。

### v1.2.2 — toggle 蓝主题化（patch 008 v4）× 检索失败外化（patch 020）（上游基线 RP-Hub 1.8.9）

> **状态：开发中（未发布）。**

**新增**
- **上游遗留蓝主题化（patch 008 v4）**：tailwind.config blue/indigo 色板接入
  `rgb(var(--tw-*) / <alpha-value>)`——luzzy 主题下全部上游遗留 blue-*/indigo-* 工具类
  （toggle 选中态、设置页叙事视角等 41+8 处）随主题收编为品牌珊瑚陶土色（与 primary
  同值）；classic 主题 = Tailwind 原值零影响；violet 徽标保留为协议功能区分色
  （v1.2.0 critique 备案）。DESIGN.md「Do's & Don'ts」收编清单与技术契约同步。
- **向量检索失败外化（patch 020）**：向量分桶检索失败（分片嵌入商/模型与当前配置
  对不上导致整桶跳过等场景）由仅 console.warn 改为 toast 提示（注入检索与手动检索
  两处 catch；30s 全局节流防离线刷屏；showToast 不可用时 try/catch 自动降级）。

**注意事项**
- EXTRACT_VERSION 15→16，安装即自动重新解压资产。
- 本版实施前按硬性规定 9 复读 4 项设计 SKILL；视觉方向=「品牌色收编」（v1.2.1 已选定
  方向）的延续迭代，豁免三方向门（豁免理由落档 WORKLOG 会话 18）。
- 视觉验证（toggle 变珊瑚 / classic 对照原蓝）待模拟器/真机走查后转正式版。

### v1.2.1 — 侧栏品牌化 × 主题预览交互化 × 记忆链路修复与内容管理器 × 品牌色收编 × 上游标记体系（上游基线 RP-Hub 1.8.9）

> **状态：已发布（GitHub Release v1.2.1 附 APK，versionCode 8）。**

**新增**
- **侧栏品牌化与导航调整（patch 019）**：侧边栏顶部品牌字样 RP HUB → **LuzzyRP**
  （Luzzy 主字 + RP 品牌珊瑚，双色同构开屏字标）；底部簇顺序调整为 **外观 → 设置 → 关于（置底）**。
- **主题预览交互化（patch 019）**：外观页预览卡色板随主题取色（classic 显示上游原版蓝灰、
  luzzy 显示暖幕手记色板）；luzzy 主题下亮/暗双卡可直接点击切换模式（选中态 ring 标识 +
  aria-pressed，200ms ease-out 按压反馈），经典主题仅亮色单卡（经典无暗色模式）。
- **记忆内容管理器**（记忆系统页）：角色选择器（多分支角色附分支选择器）跨角色查看指定角色的
  向量分片与总结记忆全量列表（分页、轮次/嵌入模型徽标、两行预览点击展开）；支持
  **编辑 / 删除 / 参与召回开关 / 清空此角色记忆**；当前角色的改动即时联动会话上下文。
- **上游标记体系（硬性规定 10）**：全部二创改动在上游文件内携带 `[LuzzyRP patch NNN]`
  标记注释（001-012 存量补全）；`tools/patches/entities/` 新增实体 diff（上游 1.8.9 基线 →
  当前态逐文件，007/009/012-019 全覆盖）并接入 `apply-patches.ps1` 自动重放判定；
  新增 `tools/verify-markers.ps1` 校验门（标记完整性 + NSFW/styles 敏感文件指纹校验），
  同步全绿才算完成。

**优化**
- **品牌色收编**：开屏加载动画（背景蓝晕/光带/底盘阴影/LUZZY-RP 字标渐变/下划线条/嵌入页
  spinner 共 7 处）与设置页两处渐变横幅（用户设置/高级设置）由蓝色系收编为 Luzzy 品牌
  珊瑚陶土色（`primary-*` token，亮暗自适应）；仅 luzzy 主题生效，经典主题保持上游原版；
  DESIGN.md 新增「禁新增裸 blue/indigo/violet 色相类」规则。
- **开屏主题防闪蓝**：head 内联主题快照脚本 + luzzy-theme.css 移入 head +
  扩展层主题快照维护（patch 018）——冷启动首帧即当前主题色，消除「蓝色一闪再变暖」。

**修复**
- **v1.2.1 布局异常（顶部遗漏字段/底部溢出屏幕）**：根因为 patch 018 对 head 的第二段
  注入丢失 `<script>` 开标签——裸露的 `document.write` 文本被解析器判定为正文起点，
  head 提前关闭、body 提前开始，裸文本渲染到页面顶部且 `luzzy-theme.css` 主题底座
  （patch 008 色板依赖的 `--tw-*` 变量）加载失败，导致全应用配色/布局崩坏。
  补回开标签即修复（仅 1 行，未触碰上游区块）；以 parse5 浏览器同源解析器做树级对比
  复验：DOM 结构 = v1.2.0 基线 + 预期插入。工作日志会话 14 的「缺 2 个 `</div>`」
  假设经证伪（正则计数盲区），entities/012-018-index-html.patch 已按修复后状态重建
  （正/反向 apply 双 PASS）。
- **上下文查看器不显示「角色记忆（向量召回）」标注**：向量召回块被相邻用户消息合并后
  标注判定失效（分片实际已在请求内）——召回块现在禁止合并（patch 016 `_preventContextMerge`），
  查看器的紫色记忆标注、「已注入 N 个向量分片」统计与高亮恢复可靠显示。
- **应用图标粉底**：自适应图标背景色 `ic_launcher_background` 在 v0.2.0 被改为粉红
  `#FF6EC7`，新装包触发桌面重新渲染后粉底显形——现改回全透明（`#00000000`），
  恢复透明贴纸效果；图标画作资源零改动（仍遵守禁止重新生成）。
  发布页 APK 已替换为修复后构建（versionCode 不变，可直接覆盖安装）。

**注意事项**
- 真机（小米 df97f3c4）release 包已逐项验证：品牌字样/导航顺序/预览卡亮暗切换/经典主题
  预览联动/杀进程主题持久化全部通过；EXTRACT_VERSION=15，安装即自动重新解压资产。
- 本版标题补「侧栏品牌化」（patch 019 内容并入本版发布）。
- **记忆面板悬浮于所有页面底部的问题已修复**（v1.2.1 结构改动曾使记忆视图的
  引擎设置/向量检索卡脱离视图 v-if 常驻渲染；已通过基线还原+重放修复，
  本次模拟器复验通过）。
- 本版引入**硬性规定 10「改动标记与上游同步适配」**（HARD_REQUIREMENTS.md 新增；
  AGENTS.md §4 同步演进）：上游文件二创改动必须带标记注释、重放通道唯一、
  同步后必须 `verify-markers.ps1` 全绿。
- 向量分片文本编辑保存时会**使用原嵌入模型重新生成向量**，嵌入模型不可用或网络失败时
  不会落盘（防文本/向量错配的脏分片；模拟器已实测失败路径：fetch 失败 → toast + 不落盘）。
- 排查结论备档：记忆链路本身（提取→嵌入→分桶检索→注入）经罐装端到端验证无回归；
  「看不到注入」另有两个非缺陷因素——保留窗口（默认 50 楼内轮次防重复不注入）与
  相似度阈值 0.45。
- 会话 17 文档回写（纯文档，不影响安装包）：README 状态徽章/版本规划表/硬性规定计数同步为
  「已发布/10 条」；AGENTS §1.1/§3.1/§1.5/§9 陈旧值修正（9→10 条、verify-markers 39→41 项、
  EXTRACT 14→15、真机复验状态、遗留待办复核）；HARD_REQUIREMENTS 规定 2 守护落点登记计数
  修正为 001-019（按 AGENTS §1.1 于本 CHANGELOG 声明）。
- 会话 17 工作区整洁（硬性规定 7）：移除 v0.x 旧工程遗物（AGENT-GUIDE / INVARIANTS-CHECKLIST /
  audit / PLAN-v0.1.0 / task 任务书 / trpg 世界卡）与大型参考资料入库（game-icon-pack /
  lobe-ui-master / rikkahub-master / D&D SRD / 字体源 / brand-logos，合计约 1.24 万入库文件），
  另移除 APK 内无任何代码引用的 `app/src/main/assets/CHANGELOG.md`（v0.2.0 时代残留）；
  .gitignore 增防回流规则；补打 `v1.2.1-r2` tag（对齐 Release APK 实际构建点 7976aba0）。

### v1.2.0 — 聊天页统一雾纸玻璃 × 外观/关于独立页 × 三协议供应商 × 自定义生图（上游基线 RP-Hub 1.8.9）

**新增**
- **聊天页液态玻璃补全（统一雾纸）**：AI/用户/system 气泡、生成中 loading 气泡、思考卡片、
  名字标签全部纳入雾纸玻璃族（`rgba(纸色 token/.74)` + `blur(18px) saturate(1.2)` + 发丝线，
  暗色同构）；角色立绘可从气泡后透出；流式生成中自动加厚至 0.88 保正文可读，完成后回落全玻璃；
  消息操作工具条收编（上游自带 blur 被移动端 kill-switch 打死）；支持 `@supports` 实底降级；
  配方单点可调（`--luzzy-glass-alpha/--luzzy-glass-blur`）。
- **侧边栏「外观」独立页**：主题/模式/字体/对话字号独立成页（侧栏底部簇：高级组 → 外观 →
  关于 → 设置置底），外观设置全应用唯一入口；主题预览条实时展示亮暗色卡与字体样张；
  v1.1.0 的外观弹窗与设置页入口卡移除。
- **侧边栏「关于」页**：品牌信息（版本号/上游基线/CC BY-NC 署名/GitHub 入口）+
  **应用内 CHANGELOG**（同步仓库更新日志，markdown 渲染）。
- **供应商三协议**：自定义供应商支持 **OpenAI / Anthropic / Gemini** 三种接口协议
  （Messages API / GenerateContent API 适配：system 抽出、图片 base64 转换、thinking/reasoning
  流式解析、embeddings 走 batchEmbedContents；Anthropic 接口不支持嵌入时明确禁用提示）。
- **供应商编辑器（二级弹窗）**：编辑供应商 ID/名称/协议/API URL/API Key；**模型手动增删改**
  （请求 id / 显示 id / 上下文长度 / 最大输出长度 / 输入模态 文本·图像·视频多选 / 模型类型
  文本·图像·嵌入 单选 / 自定义请求体）；供应商级+模型级自定义请求体（键值行懒编辑，如
  `reasoning_effort: max`）；**模型 id 热检测预设**（glm-5.3 / glm-5.3-flash / deepseek-v4-pro /
  deepseek-v4-flash / deepseek-v4-flash-vision-exp，大小写不敏感、长词优先，只填空字段可撤销）。
- **长度字段生效（注入+展示）**：最大输出长度按协议注入 `max_tokens` / `maxOutputTokens`
  （Anthropic 必填字段自动补默认）；上下文长度在模型选择器以 meta chip 展示（如 `1M · 文本+图像`）。
- **模型列表热更新**：编辑器保存后手动模型即时进入聊天/识图模型选择器（与 /models 拉取结果
  合并展示）；编辑供应商 ID 时全部槽位引用与 Key 自动重映射。
- **自定义生图模型**：生图设置新增「模型来源」——STA1N 官方（默认不变）/ 自定义模型
  （OpenAI 协议 image 类型模型，走 `images/generations`，b64 直出，重生成/比例同步支持）。

**优化**
- 供应商管理列表改卡片式回显（名称/协议徽标/URL/Key 掩码/检测按钮），Key 编辑收进编辑器；
  「添加供应商」按钮文案更新为三协议。
- 经典主题零影响：全部玻璃配方仍以 `:root[data-theme="luzzy"]` 作用域。

**修复**
- **（自检轮）三协议适配端点修正**：anthropic/gemini 请求现正确剥离 OpenAI 路径后拼各自
  端点（此前 anthropic 会 POST 到 `/v1/chat/completions`、gemini 拼出损坏 URL——仅影响
  非 OpenAI 协议的真实对话）；连续同角色消息自动合并（两家 API 要求交替）；
  Anthropic thinking 预算越界守卫（`max_tokens` 过小时不启用思考）。
- **（自检轮）自定义生图**：修复重生成崩溃与提示词恒为占位符（`$1` 未被替换）的问题。
- **（自检轮）手动模型即时可见**：编辑器保存即写入模型缓存（此前无 Key 的供应商的手动模型
  不进选择器）；模型 meta（上下文长度/模态）在选择器正确显示。
- **（自检轮）热检测预设**：逐字输入 `glm-5.3-flash` 不再锁死 `GLM-5.3` 短标签；「撤销」
  精确作用于触发行；预设填充的请求体在输入框正确回显。
- **关于页 GitHub 入口**：新增原生桥 `openUrl`（系统浏览器打开）——WebView 内
  `window.open` 无效的问题顺带覆盖历史外链场景。
- 「外观」按钮无激活高亮（v1.1.0 遗留）。
- 思考卡玻璃 alpha 过高导致的「雾纸不透」观感（0.86→0.74 对齐统一配方）。

**注意事项**
- 供应商「最大输入长度」字段为有意不设：上下文长度即 输入+输出 总预算，服务端按自家
  tokenizer 精确计数；客户端无需（也不应）单独填写。
- 视频输入模态为能力预留字段（上游无视频附件入口，实际请求暂不发送视频内容）。
- 自定义生图当前仅支持 OpenAI 协议模型；Gemini/Anthropic 图像模型接入列入后续版本。

**构建**：assembleRelease 通过（arm64-v8a / universal / x86_64 三件套）；versionCode 7；
`EXTRACT_VERSION` 5→6（升级后自动重新解压资产，IndexedDB 用户数据不受影响）。
模拟器（LuzzyRP_Test / Android 15）全量走查通过：外观/关于页渲染与持久化、供应商编辑器
端到端（三协议新增/编辑/删除、五组热检测预设、Key 写回、删除回落）、三协议罐装 SSE 解析
（text/reasoning/usage/请求体结构）、max_tokens 注入与请求体合并、玻璃亮暗 computed 四值命中；
证照 `docs/design/verify-v120-*.png`。

### v1.1.0 — 多模型商混用 × 思考卡雾纸玻璃 × 外观独立面板（上游基线 RP-Hub 1.8.9）

扩展功能第一批（对应 README 版本规划 v1.1.0）。全部改动登记为 patch 012 / 013，扩展层视觉零新 patch。

**新增**
- **多模型商混用（供应商管理器）**：设置页供应商下拉新增「管理供应商…」，支持任意数量自定义 OpenAI 兼容商（新增 / 改名 / 改地址 / 改 Key / 删除），内置 STA1N / DeepSeek / OpenRouter / SiliconFlow 可分别配 Key；每商「检测」按钮一键验证连通性并返回模型数，配置状态点（绿/灰）直观可见。
- **模型来源标注 `[商名]`**：模型存储升级为 `商id::模型id` 复合引用，全部模型展示位（模型选择弹窗 / 快捷三槽位 / 聊天页齿轮弹层 / 设置页聊天·识图·UI模板·嵌入·总结副模型入口 / 用量统计日志）以 `[商名] 模型id` 标注来源，商名为珊瑚色徽标 chip——同一模型型号来自不同模型商从此一目了然，可跨商混选。
- **跨商合并模型列表**：模型选择器展示全部已配置商的模型合集（按商缓存、打开时惰性拉取），族谱标签与搜索照常可用，搜索可命中商名；手动「刷新可用模型列表」一次拉取全部供应商。
- **请求级解析层**：主聊天 / 识图 / UI 模板副模型 / 记忆总结 / 记忆嵌入全部按模型引用自动路由到对应供应商的地址与 Key（未配置或裸 id 回落当前激活商，老数据零迁移直接可用）。
- **记忆双模式跨商**：总结模式副模型、向量模式嵌入模型均可选自任意供应商；向量检索按（供应商, 模型）分桶、每桶以该商该模型现算查询向量（桶内自比较，跨商余弦有效）；旧向量分片自动归入 legacy 桶按原行为检索。
- **思考卡片全卡雾纸玻璃**：聊天页模型思考卡整体磨砂玻璃化（blur 16px 暖 tint + 发丝线 + 展开态暖阴影，内部步骤详情半透），流式生成中自动降级近实底 + 珊瑚描边点缀，生成完成恢复全玻璃；不支持 backdrop-filter 时实底兜底（三方向硬门用户选定方向）。
- **外观独立面板**：侧边栏新增「外观」入口（设置按钮下方），点开居中模态面板集中管理界面主题 / 模式 / 界面字体 / 对话字号，弹层背后实时预览、即时生效自动保存；设置页「高级参数」保留同名入口卡。

**优化**
- 供应商管理、外观面板弹窗自动继承雾纸玻璃层亮暗主题，无额外适配；
- 老用户「自定义 / 自定义2」槽位自动迁移为管理器中的自定义供应商（原字段保留，小说工坊不受影响）；工坊侧激活商为自定义商时自动映射传递；
- Token 用量记录新增供应商维度，跨商用量可分辨；
- 角色卡生成器同步载荷剥离商前缀，生成器语境不受复合引用影响。

**注意事项**
- 向量检索新增按桶现算查询向量：向量记忆横跨多个供应商时会逐桶调用嵌入接口（每桶一次），嵌入调用量与桶数成正比；
- 删除仍被引用的自定义供应商时，相关模型槽位会回落为「跟随当前 API 预设」并弹确认告知；
- 生图模型走独立网关，不参与多商混用（与上游行为一致）。

**构建**：`assembleRelease`（luzzy 签名 + R8 + ABI 拆分）· versionCode 6 · 附 APK。EXTRACT_VERSION 4→5（assets 变更触发重新解压，用户数据不受影响）。真机（小米 25098PN5AC / Android 16）实测：玻璃四态 computed 命中、外观面板 / 供应商管理器 / 跨商徽标列表全通过。

### v1.0.0 — 正式版 · 重建落地：暖幕手记主题 × 雾纸玻璃 × RP-Hub 二创壳（上游基线 RP-Hub 1.8.9）

v1.0.0 线定稿（rc1 → rc2 → rc3 → 正式版；rc 期间全部变更随正式版一并交付，细节见下方 rc 条目）。
相对 v0.3.0（旧 Compose 工程，WIP 不可游玩）为**推倒重建**：引擎与代码库完全更换，旧版数据不通用。

**新增**
- **RP-Hub 二创壳**：RP-Hub 1.8.9 前端 + Kotlin 单 Activity WebView 壳 + `assets/ext/` 独立扩展层；上游同步走「覆盖 + 登记 patch 重放」（patch 001-011），品牌化 / 禁更新检查 / 扩展挂载全部 patch 化，上游文件零裸改。
- **全离线**：Vue 3 / Tailwind / marked / DOMPurify / SortableJS / Lora / 阿里系字体全部本地打包，运行时零 CDN 依赖（断网可用）。
- **主题系统「暖幕手记 × Claude」**：Claude token 体系（cream `#FAF9F5` 画布 + 珊瑚陶土 `#CC785C`/`#A9583E` + ink `#141413`）亮暗双模式；暗色 gray 色阶整体反转适配全部上游工具类；新用户默认新主题，老用户保留经典（迁移逻辑）。
- **雾纸玻璃层 Frost-Paper**：玻璃只上固定 chrome（顶栏 / 输入岛 / 侧栏抽屉 / 模态面板，blur 16px），聊天气泡回归纸感；突破上游移动端 `backdrop-filter` 全局 kill-switch 与 `!important` 白面死角。
- **字体系统**：`Luzzy 默认`（AlibabaSans + Alibaba PuHuiTi 3，本地 woff2）/ 经典（原版）/ 经典衬线（Lora）/ 系统，四选项随主题设置。
- **系统能力桥**：角色卡 PNG/JSON 走 SAF 文件桥导入导出；剪贴板 / Toast / 版本信息 / 系统栏样式联动主题亮暗。

**修复**
- rc1 → rc3 全程真机热修：主题不生效（Tailwind 透明度工具类必须 RGB 三元组 + `<alpha-value>`）、暗色白块、上游 `!important` 白面死角、移动端磨砂 kill-switch（详见 rc 条目）。

**注意事项**
- **旧版（≤ v0.3.0 Compose 工程）数据不通用**，全新安装即用。
- 侧载分发（不上架商店）：安装需允许「未知来源应用」。
- NSFW 增强预设（上游 `<nsfw_rules>`）原样保留、不可触碰（硬性规定 1）。

**构建**：`assembleRelease` BUILD SUCCESSFUL（luzzy 签名 + R8 + ABI 拆分三件套）· versionCode 5（衔接 v0.3.0 的 4，覆盖安装无降级拦截）· 附 APK。

### v1.0.0-rc3 — 雾纸玻璃层 Frost-Paper · 液态玻璃方向融合（上游基线 RP-Hub 1.8.9）

在「暖幕手记」主题上融合液态玻璃 / 半透明容器语言：按硬性规定 9 重读 4 项设计 SKILL，出 3 块真实方向板（`docs/design/boards-v3/`，亮暗双框同场景渲染），用户选定**方向 A「雾纸 Frost-Paper」**（参照 Windows 11 Mica，存档 `direction-approved-v3.md`）。

**新增**
- 雾纸玻璃层：玻璃只上**固定 chrome**——顶栏 / 输入岛 / 侧栏抽屉 / 模态面板（亮 cream `rgba(250,249,245,.86-.88)` / 暗 暖纸 `rgba(32,30,27,.86-.88)` + blur 统一 16px + 发丝线边）；聊天气泡**回归不透纸面**并移除 backdrop blur（亮 `#F5F0E8` / 暗 `#2B2824`，GPU 同步减负）；`@supports` 不支持时自动实底降级。全部落在 `luzzy-theme.css` 扩展层（硬性规定 3），**零新 patch**；DESIGN.md 新增 Glass 章作为真源。

**修复**
- rc2 暗色死角：上游 styles.css 在移动端媒体查询内以 `!important` 写死一批白面（`.input-island` 输入岛、`.typing-bubble`、`.msg-name-tag`、`.typing-timer-badge`、`.toast-item`、`.desc-panel`、`.wi-footer`），暗色下仍呈白色——主题层以同等级 `!important` + 更高特异性成建制收编（CDP 实证 `.input-island` 由白 0.9 → 暖纸 0.88）。
- 抽屉遮罩由上游 slate 冷蓝改为墨色暖黑（亮暗通用）。
- **上游移动端 backdrop-filter 全局 kill-switch**（`* { backdrop-filter: none !important }`）导致手机上从未有过磨砂——雾纸层以更高特异性 `!important` 仅对 chrome 表面精准放行；真机（小米 25098PN5AC / WebView 150）实测磨砂渲染生效（条幅探针证照 `docs/design/verify-frost-phone-{light,dark}.png`）。

**注意事项**
- `EXTRACT_VERSION` 2→4（assets 变更触发重新解压，IndexedDB 用户数据不受影响）。
- CDP `captureScreenshot` 在页面有激活 backdrop-filter 时会挂起（Chromium 合成读回互锁）——真机视觉验证走 `adb shell screencap`。

**构建**：`assembleDebug` 通过（universal 42,750,291 字节 ≈ 40.8MB）；versionCode 1（debug 后缀）。

### v1.0.0-rc2 — 全新主题「暖幕手记 × Claude」· 设计 SKILL 三方向硬门（上游基线 RP-Hub 1.8.9）

按用户指令推翻 v1.0.0-rc1 的「暖纸书房」主题（patch 008-011 全部撤销、上游文件与参考克隆 diff 归零），依据硬性规定 9 走完 4 项设计 SKILL（huashu-design / awesome-design-md / open-design / ui-ux-pro-max）完整流程后重新设计。

**新增**

- **设计流程物**：三方向差异化方向板（A 锐白 Swiss Monochrome / B 午夜场 ElevenLabs 参照 / C 暖幕手记 Collins）＋共享骨架＋设计合同 spec-v2（`docs/design/boards-v2/`、`docs/design/spec-v2.md`）；用户选定「C，进一步增强 Claude 风格」，落档 `direction-approved-v2.md`。
- **新主题「暖幕手记」**（C × Claude token 体系，DESIGN.md 真源重写）：亮色 = tinted cream 画布 #FAF9F5 + 暖表面三层（#F5F0E8/#EFE9DE/#E8E0D2）+ Claude coral #CC785C（图形）/ #A9583E（按钮，白字 4.7:1）+ ink #141413；暗色 = Claude 暗表面系（#181715/#1F1E1B/#252320）+ coral 提亮 #D97757，gray 色阶反转使上游全部工具类自动适配；名字标签走 Lora 衬线（剧作手记的文学声音）；正文对比度亮暗分别 ≥4.5:1。
- **设置页主题卡重构**：「界面主题」卡——主题选择（暖幕手记/经典）+ 模式选择（亮/暗，仅 Luzzy 主题显示）+ 界面字体（附属设置并入主题卡）；新用户默认暖幕手记+亮色，老用户（savedSettings 无 theme 字段）迁移保留经典。
- **字体选项改版**：上游内置字体改「经典」系命名——`经典（原版）`/`经典衬线（Lora）`/`系统`，新增 `Luzzy 默认`（AlibabaSans 拉丁 + Alibaba PuHuiTi 3.0 中文，本地打包）；新用户默认 Luzzy 字体。
- **暗色白块治理**：patch 008 升级 v3（RGB 三元组 + `<alpha-value>`）——v2 纯 var() 下带透明度修饰符的工具类（bg-gray-50/60 等）被 Tailwind JIT 回退纯白，是暗色白块的机制性根因；另以 !important 覆盖暗色 `bg-white`/`bg-white/*` 与上游写死白色的 segmented 滑块，暗色画面纯白块清零（CDP 全 DOM 扫描实证）。
- **壳层配套**：debug 构建开启 WebView 远程调试（CDP，release 不受影响）；系统栏桥接改「状态栏图标恒白（顶栏深渐隐双向可读）+ 导航栏图标随主题明暗」；windowBackground 渐变暖化为 #8B8886→#7D7A77→#FAF9F5 与 cream 画布衔接。

**修复**

- 旧实现的隐患修正：上游参考克隆 diff 归零验证；迁移逻辑改置于 `if (savedSettings)` 块内（避免新用户无存储时 `hasOwnProperty.call(undefined)` 抛 TypeError）。
- Tailwind Play CDN 对 var() 色值的兼容性经 jsdom 实证**成立**（推翻 v1.0.0-rc1 移交的「CDN 拒绝 var()」根因假设）；rc1 真机「主题未生效」的真因判定为 CSS 变量未定义（透明透出 windowBackground，黑渐隐叠加色数学与实测截图吻合）。

**注意**

- 主题验证基线（模拟器 + 真机双端，CDP 数据面）：亮 `body=#FAF9F5`、暗 `body=#171614`、经典回退 `#f9fafb`；暗色层次重调后弱/次级文字对比 4.9:1 / 6.8:1、卡片与画布拉开展次、透明度变体正常着色、纯白残留 0；真机（小米 25098PN5AC）走 EXTRACT_VERSION 2 保数据升级，老用户设置正确沿用——对照 rc1 同机 #BCBDBE，P0 确证修复；截图存档 `docs/design/verify-v3-{light,dark}.png` 与 `verify-v3-phone-{light,dark}.png`。
- 修改 assets 后必须卸载重装或 bump `AssetExtractor.EXTRACT_VERSION`，否则 filesDir 不会重新解压（本版验证时踩过）。
- 上游硬编码的非 gray/primary 色（indigo/blue/pink 工具类）不在 v1 主题范围，保持原样；后续可按 DESIGN.md 扩展。

构建：`assembleDebug` BUILD SUCCESSFUL · versionCode 1（debug）

---

### v1.0.0 — 重建 · RP-Hub 二次开发 · 原生 WebView 壳（上游基线 RP-Hub 1.8.9）

全面推翻旧 Kotlin/Compose 工程，转为对开源项目 RP-Hub（STA1N156，CC BY-NC 4.0）的二次开发：原生 WebView 壳 + 独立扩展层。旧工程备份于 git tag `legacy-v0.3.0`。

**新增**

- **主题系统**：设置页新增「界面主题」——经典（原版 RP-Hub 浅色）/ 暖纸书房（Luzzy 新主题，亮/暗双模式）；新用户默认暖纸书房；老用户保留经典（迁移逻辑）。主题由 `data-theme` + `data-mode` 双属性驱动，tailwind.config 色板 var() 化（patch 008-011）。
- **暖纸书房主题**（方向 A，用户选定）：米纸底 #FAF9F5 + 烤橙 #D97757（图形 accent）/ #B85C3E（按钮）/ #A8543A（文字）双档策略过 4.5:1；暗色深暖灰 #262624 + 暖橙 #E08A5F；卡片式分层布局（气泡悬浮卡 + 输入区悬浮工具栏）；动效令牌进入 200ms / 退出 140ms / ease-out cubic-bezier(0.23,1,0.32,1)；系统栏图标深浅联动（LuzzyBridge.setSystemBarStyle）。
- **字体设置**：界面字体新增「Luzzy 默认字体」选项（Alibaba PuHuiTi 3.0 中文 + AlibabaSans 拉丁，本地打包 15.8MB）；新用户默认 Luzzy 字体；经典（modern/serif/system）保留。
- **壳工程**：单 Activity WebView 宿主（Kotlin，仅 4 个最小依赖）；首次启动 assets 解压到 filesDir（标记版本幂等，localStorage 持久化保障）；返回键 WebView 回退；`onActivityResult` 文件选择桥。
- **JSBridge 原生层**：剪贴板 / Toast / 版本信息（versionName、versionCode、上游版本）/ 设备信息 / 系统栏样式；R8 keep 规则保护方法名。
- **文件桥**：`onShowFileChooser` SAF 导入（角色卡 PNG/JSON）；DownloadManager 导出落盘 Download/LuzzyRP/。
- **资源离线化**：Vue 3 / Tailwind CDN / marked / DOMPurify 3.0.6 / SortableJS / daisyUI 4.7.2 / localforage 1.10.0 全部本地打包至 `vendor/`；Lora 可变字体（400-700 + italic）本地打包；主页面 + character + novel 子页面 CDN 引用全部本地化（静态资源 CDN 清零）。
- **品牌化**：标题 / 入口 logo（LUZZY·RP）/ 应用名 / 图标复用；`rphub-update-api` 移除（禁用上游更新检查）。
- **扩展层**：`luzzy-bridge.js`（桥接封装 + 降级）/ `luzzy-theme.css`（字体栈覆盖 + 主题变量）/ `luzzy-ext.js`（桥接自检 + 关于页品牌注入 + 主题应用）；index.html 尾部挂载，与上游零冲突。
- **文档体系**：README 重写（二创署名声明 + 完整门面）；AGENTS.md（后续 Agent 工作指南 + 硬性规定 9 设计 SKILL 强制条款）；HARD_REQUIREMENTS 重写（9 条）；PLAN-v1.0.0（10 决策 + 9 Phase）；DESIGN.md 设计真源（暖纸书房定稿）；docs/design/（theme-spec / theme-tech-plan / theme-motion-plan / direction-approved / direction-summary）。
- **同步机制**：`tools/sync-upstream.ps1`（fetch → 覆盖复制 → patch 重放 → 指纹更新 → 回归清单）；`tools/apply-patches.ps1`（幂等 patch 重放，001-011 登记）；`tools/upstream-fingerprints.txt`（11 文件 SHA-256 基线，NSFW 协议守护配套）。

**优化**

- APK 体积：旧工程全量 ≈ 830MB 源码 → 壳 + 离线资源 + 字体 debug APK ≈ 40.8MB（ABI 拆分三件套）。

**注意**

- 应用为**侧载分发**，不上架商店（nsfw_rules 年龄条款合规风险）。
- 上游基线：RP-Hub 1.8.9（commit b409ca6）；同步 SOP 详见 AGENTS.md §4。
- CJK 分片字体（Ma Shan Zheng / Noto Serif SC）未本地化，novel 页艺术字体降级为系统衬线（已登记 patch 007 决策）。
- 主题系统依赖 patch 008-011，上游同步后必须重放（apply-patches.ps1 已登记）。

构建：`assembleDebug` BUILD SUCCESSFUL（AGP 9 内置 Kotlin）· versionCode 1

---

## 历史区（v0.x · 旧 Kotlin/Compose 工程）

旧工程全部记录保留于 git tag `legacy-v0.3.0` 对应版本的 CHANGELOG 页。以下为存档：

### v0.2.0 — 设计重构 · 角色卡生态 · 预设与档案

按用户检阅反馈全面迭代：4 项设计 SKILL 方法论落地（硬性规定 13）、角色卡生态补全、菜单重构与启动直达。

**新增**

- **设计体系落地**：4 项设计 SKILL（huashu-design / open-design / awesome-design-md / ui-ux-pro-max-skill）全部存档至 docs/skills/ 并写入硬性规定 13；新增仓库根 `DESIGN.md` 设计契约与 `docs/AGENT-GUIDE.md` 开发指南。
- **图标黑边修复**：815 枚图标管线升级——索引色 PNG → RGBA 清洗（透明区杂色归零）+ 字形 bbox 归一化居中（全库视觉大小统一）+ 透明边缘平滑；启动图标黑晕清除、底色改 AuroraPink。
- **启动直达**：应用启动自动打开上次会话；首次启动默认创建并进入「鹿溪」对话。
- **角色卡生态**：手动新建角色卡；头像选择（自动 1:1 裁剪）；聊天背景图（默认回落头像）+ 透明度；`<CUT>` 分割多条开场白（多气泡输出）；世界书整体并入角色卡二级页。
- **世界书编辑器**：条目全字段管理——条目名称/内容/激活策略（常驻+关键词，可叠加）/激活概率 0-100/注入位置（↑Char、↓Char、↑EM、↓EM、@Depth×system/user/assistant + 深度）/独立启停；SillyTavern 世界书 JSON 导入（外部条目默认启用）。
- **提示词预设**：预设 = 名称 + 条目列表；条目独立启停/命名/角色（SYSTEM/USER/ASSISTANT）/注入位置（相对 + @Depth×3）/内容编辑；单选激活注入 system。
- **用户档案**：头像/名字/身份，注入 system 稳定前缀。
- **思考深度自适配**：按模型 id 检测家族自动给出正确档位与请求字段——DeepSeek（none/high/max，官方三档）、GLM（thinking.type / 5.3+ 仅 reasoning_effort）、GPT（reasoning_effort）、Opus（adaptive thinking + output_config.effort，最高 xhigh）；模型级温度/深度覆盖全局。
- **供应商管理增强**：新增/编辑/删除供应商（含内置）；新增模型表单全字段（id/显示名/上下文长度/最大输出/温度/思考深度）；**单位换算**（1024000、1024K、1M、1024k、1m 均可填写）；字段自检（即时校验 + 错误提示）；生成参数并入供应商页。
- **应用日志**：记录用户步骤/模型步骤（请求组装、生成完成、失败堆栈）/工具轮次，内存实时查看 + JSONL 落盘（保留 3 天）+ JSON 导出（SAF 自定义路径）+ 系统分享；关于页内嵌 CHANGELOG 渲染。

**重构**

- 菜单精简为：聊天 / 角色卡 / 预设 / 用户档案 / 设置；历史会话与搜索并入聊天页顶栏；世界书并入角色卡；长期记忆并入设置（记忆设置页）；移除收藏入口。

**数据**

- Room v1 → v2 手写迁移（worldbook_entries.depthRole 列 + prompt_presets 表），老用户数据无损升级。

构建：`assembleRelease` BUILD SUCCESSFUL（R8 minify）· versionCode 3


### v0.1.1 — 首航热修：模拟器实测三缺陷修复

v0.1.0 在 Android 15 模拟器全流程实测（安装 → 启动 → 新建会话 → 开场白渲染 → 发送/错误路径）后发现并修复 3 个缺陷。

**修复**

- **启动崩溃**：`painterResource(R.mipmap.ic_launcher)` 无法加载自适应图标（AdaptiveIconDrawable 既非 Vector 亦非位图，首帧即抛 IllegalArgumentException）→ 品牌 logo 以 PNG 资源（`luzzy_logo`）入 drawable，全部 Compose 引用替换。
- **顶栏遮挡**：edge-to-edge 下未处理 WindowInsets，首页/聊天页顶栏切入状态栏 → 全局 Shell 增加 `systemBarsPadding()`。
- **种子数据缺失**：内置角色卡「鹿溪」/ 其世界书 / 正则内置预设的 `ensure*()` 从未被调用 → 应用启动时幂等初始化（新建会话现在正确显示「与鹿溪 的故事」并携带完整开场白）。

**优化**

- 发送后无可用供应商时以错误条提示（errorContainer），不再静默。

构建：`assembleRelease` BUILD SUCCESSFUL（R8 minify）· versionCode 2


### v0.1.0 — 首航：全新引擎 · 全新起点

LuzzyRP 的第一个正式版本。全新代码库，以 rikkahub 已验证的流式架构为实现蓝本，将「真流式输出」与「Agentic 闭环」一次锚定到位。

**新增**

- **真流式输出引擎**：callbackFlow + OkHttp SSE EventSources + `trySend` 逐 event 零节流 + `readTimeout = 10 MINUTES`；思考卡片与正文气泡均以 1 字 = 1 次更新直连单一真源 `MutableStateFlow<Conversation>`。
- **三协议供应商层**：OpenAI 兼容（一等公民，覆盖 DeepSeek / 火山方舟 CodingPlan / GLM / OpenRouter 及任意兼容端点）、Anthropic messages API、Google Gemini SSE；主机思考参数自适应（thinking.type / reasoning_effort / reasoning.effort）。
- **Agentic 闭环**：`maxSteps = 256` 硬上限、工具结果**原地回填**（绝不新建 TOOL 消息）、三 break 条件、`ToolApprovalState` 五状态机审批与续跑、禁用 `tool_choice = required`；RP 模式两阶段闭环 `maxLoops = 3`（工具轮次上限 → 阶段二基于结果生成正文）。
- **文本标签兜底**：`<tool_calls>` 标签流式解析器（GLM-5.2 等无原生 function calling 模型），支持跨增量切分安全、行式/JSON 数组双格式、截断尽力解析，协议文本永不上屏。
- **内置提示词强化**：Agentic 行为协议（>2 轮思考、>1 次主动工具调用）注入稳定前缀；NSFW 块占位文件（由用户手动填写，任何逻辑不可触碰）。
- **角色卡生态**：SillyTavern v2/v3 PNG 导入导出（自研 PNG tEXt chunk 读写器）+ JSON 导入导出、头像 1:1 裁剪、内置默认角色卡「鹿溪」（只读保护）。
- **世界书三策略召回**：常驻 / 关键词（含递归扫描与概率 roll）/ 向量（sqlite-vec Top-K + 相似度阈值）；注入位置分层与 KV 缓存结构对齐。
- **长期记忆（ACE）**：Execute（Top-K 注入）→ Reflect（LLM 反思 helpful/harmful/neutral 计数）→ Update（提取新事实 + 余弦 ≥0.92 嵌入去重）+ 评分淘汰；管理页可启停/删除。
- **三级摘要**：A 级每轮 ≤50 字（盲续五要素）/ B 级每 10 轮合并 ≤10 条 / C 级每 50 轮固化永久；异步执行不阻塞回复。
- **数据层**：Room v1（9 实体、8 DAO、WAL、schemas 导出、AutoMigration 就绪）+ DataStore 设置单一真源 + sqlite-vec 向量索引（维度自适应）。
- **UI（Aurora Dual）**：Material 3 Expressive + MaterialExpressiveTheme；AuroraPink/AuroraViolet 双生色板（零硬编码色值）；三态动效令牌（进入 300ms / 交互 150ms / 退出 195ms）；815 枚 game-icon-pack 图标 + lobe lucideExtra 37 枚向量图标的生成管线；阿里巴巴普慧体 3 + AlibabaSans 中西文逐字符混排（其余文字回退系统字体）。
- **页面**：首页（会话列表/置顶/搜索/抽屉）、聊天页（思考卡片时间线 + 工具卡片内嵌 CoT 列 + 双色气泡 + 流式跟随 + 回到底部 + 发送/停止切换）、角色卡库与详情、世界书、长期记忆、收藏、历史会话、设置族（供应商/生成参数/外观/关于）。

**优化**

- KV 缓存命中：消息序列化稳定化（固定字段序、紧凑 JSON、时间戳不进前缀）+ 三层提示组装（稳定前缀系统消息 → append-only 历史 → 尾部动态块）。
- 分支会话模型：重新生成产生兄弟节点，selectIndex 当前分支指针，历史完整保留。
- ABI 拆分（arm64-v8a / x86_64 / universal）+ R8 minify + shrinkResources。

**守护**

- 单元测试全绿：流式合并代数、标签兜底解析、PNG tEXt 往返、KV 前缀稳定性 Golden（同历史两次组装逐字节相等）、Agentic 循环（原地回填/审批续跑/两阶段收口）。
- 不变性落点文件均带 `[INVARIANT-STREAMING]` / `[INVARIANT-AGENTIC]` / `[INVARIANT-KV]` / `[INVARIANT-NSFW]` 守护注释块。

**注意事项**

- 使用前请在「设置 → 供应商」配置 API Key（内置 DeepSeek 与火山方舟 CodingPlan 档案，均可编辑）。
- 向量召回（记忆/世界书）需在设置中另行配置嵌入模型；未配置时自动退化为关键词召回。
- NSFW 提示词块为占位状态，由用户手动填写（`NsfwBlock.kt`），项目本身不做任何内容过滤。

构建：`assembleRelease` BUILD SUCCESSFUL（R8 minify）· versionCode 1
---

### v0.3.0 — Aurora v2 主题重制 · 三态动效体系 · 图层设计

以 4 项设计 SKILL 为方法论本体完成的**可见视觉全面重制**（ui-ux-pro-max 检索基线 + huashu 动效纪律 + open-design DESIGN.md 契约 + awesome-design-md 结构范式）。

**主题重制（主题令牌系统 v2）**

- **AuroraColor v2**：Material3 方案完整落位——surfaceContainer 全族五档色阶、outline/outlineVariant、inverse 系、scrim、surfaceDim/Bright；亮/暗/**AMOLED** 三套独立方案（AMOLED 纯黑非暗色微调）。
- **极光渐变系统（AuroraBrush）**：Pink→Violet 主渐变 / 反向渐变 / 画布顶部氛围微渐变——发送按钮、品牌标题、主行动统一取用（渐变为品牌资产，禁止业务自拼）。
- **LuzzyElevation 图层令牌**：五层深度体系（画布→卡片 2dp→悬浮 6dp→弹窗 12dp+scrim45%→Toast 16dp）。
- **LuzzyIconSize / LuzzyCorner 令牌**：图标 16/20/24/32 四级语义尺寸、圆角五档——全项目统一，禁止随机值。
- **MotionTokens v2**：场景化动效规格（页面 slide¼+fade 300/195 快出慢入、弹窗 scale 0.92 spring、Sheet、列表 stagger 20ms、按压 scale 0.98 布局不跳动、展开 195）+ reduced-motion 替代规格。
- **Typography/Shapes v2**：字阶微调 + LuzzyCorner 接入 Material Shapes。

**组件库 v2**

- **AuroraTopBar**：统一顶栏（56dp 高/图标 24dp/单行省略/操作位规范），聊天/世界书/预设/预设编辑/用户档案/记忆 全部换装；新增 TopBarAction 规范按钮。
- **LuzzyDialog**：统一弹窗（scale+fade 三态进出、Layer3、正文左对齐、danger 变体）。
- **EmptyState**：统一空态（Hero 图标+标题+引导，spring 入场）。
- **AuroraSurface v2**：按压缩放 0.98（布局不跳动）+ 色彩过渡 + 阴影抬升三态。

**逐页重制**

- **聊天页**：背景图层正确分层（卡背景>头像，透明度直调）；气泡 v2（用户=极光淡染渐变 / AI=纸面卡 Layer1 阴影+描边）；输入栏渐变发送键；顶栏半透明融合。
- **首页**：时段问候 + 极光渐变品牌标题；会话空态引导。
- **页面转场 v2**：fade+slide 六分之一屏、缓动曲线化（快出慢入不对称节奏）。

构建：`assembleRelease` BUILD SUCCESSFUL（R8 minify）· versionCode 4

