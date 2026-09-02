# LuzzyRP v1.2.0 实施计划（玻璃补全 · 外观/关于独立页 · 供应商管理器大扩展 · 自定义生图）

> 会话 12 · 2026-09-02 · 版本 v1.1.0 → v1.2.0，versionCode 7，上游基线 RP-Hub 1.8.9 不变。
> 用户已确认：玻璃=统一雾纸；长度字段=注入+展示；**不设「最大输入长度」字段**（上下文长度即输入+输出总预算，服务端按自家 tokenizer 硬计数，客户端按 上下文−最大输出 推导输入预算）。

## 探索结论基线（实施时直接引用，file:line 为 v1.1.0 现状）

### 聊天页 DOM（index.html）
- 消息 DOM 全在 index.html（非 runtime-services.js）：气泡主体 413-420（user self `bg-blue-50/85`、AI/他人 `bg-white/70`、system `bg-red-50/70`，尾巴 `backdrop-blur-md glass-stabilize msg-bubble-glass` :420）；
- 名字 chip `.msg-name-tag bg-white/50` :406-409；typing 气泡 :735-763（`.typing-bubble bg-white/70`）；
- 操作工具条 `.message-action-bar` :641-726（styles.css:890-901 上游自带 blur(10px)，被移动端 kill-switch 打死且从未被收编）；
- 思考卡层级 :470-558：`.cot-ui.native-thinking-card`（is-live/is-open 由 Vue :class 绑 :472）→ `.cot-header` :473 → `.cot-body/.cot-inner` :490-492 → timeline 竖线容器 :493 → 步骤 marker :496 → summary 行（「原生思考 N字 详情」）:508-526 → detail `bg-gray-50` :527-528；
- 立绘：`absolute -inset-4 … blur-[2px] char-bg-blur` z-0 CSS 背景层 :196-199；无整体遮罩。

### 样式（styles.css + luzzy-theme.css）
- `.msg-bubble-glass` 仅一条 `rgba(255,255,255,0.88)!important`（styles.css:1664-1666）；气泡 blur 靠模板 Tailwind 类；
- kill-switch：@media max-width:768px `* { backdrop-filter:none!important }`（styles.css:1779-1785）；
- luzzy-theme.css：雾纸层 152-307（chrome 表面收编 + 移动端白面夺回）；思考卡 v1.1.0 玻璃 309-396（alpha 0.86/0.88/is-live 0.96）；`.msg-bubble-glass` 被强制实底 #F5F0E8/#2B2824 + backdrop-filter:none（206-216、232-239）——**玻璃不完整根因**。

### 视图系统（app.js + index.html）
- `currentView = ref('chat')` app.js:282，无持久化；视图 watch app.js:1683-1704（appearance/about 落入无副作用 else 分支，已验证安全）；
- 视图区块：settings 为 `v-if` 管理页模式（index.html:1256-2029，`settings-page-header` 惯例 :2032）；characters 惰性 v-if+v-show；
- AppSidebar ui-components.js:314-472：primaryItems :295 / onlineItems :302 / advancedItems :307；设置按钮 :442-446；v1.1.0 外观按钮 :448-453（emit open-appearance，class 写死无激活态）；底部用户卡 :455-471；
- CustomSelect（ui-components.js:30-303）：props modelValue/options/placeholder/buttonClass/menuClass；emits update:modelValue + change；options `{value,label,description?,group?}`；
- 外观弹窗（patch 013）：index.html:2902-2943，四 custom-select 绑 settings.theme/themeMode/fontFamily/fontSize；设置页入口卡 :1934-1946 + 重复字号下拉 :1947-1953；
- markdown 渲染：app setup 暴露 `renderMarkdown`（marked+DOMPurify，app.js:3458-3467），先例 `update-notification-modal`（ui-components.js:633+，index.html:2800-2801）；
- 版本桥：luzzy-bridge.js:45-57 `getVersion()`（getAppVersion/getAppVersionCode/getUpstreamVersion，带降级）；luzzy-ext.js:46-67 injectAboutBranding 找 `[class*="about"/"version"]` 容器注入（新关于页容器 class 含 about 即自动挂）。

### 请求管线（app.js + runtime-services.js）
- 主聊天 generateResponse app.js:4729-5476；resolveModelRequest :767-777；实际 fetch 委托 requestChatCompletion（runtime-services.js:146-171）：body `{model, messages, temperature, reasoning_effort?, stream, stream_options?}`，**无 max_tokens**；
- SSE：parseSsePayload runtime-services.js:43-55（delta.content + extractNativeReasoning）；readStreamingResponse :59-108；reasoning 抽取 core-utils.js:250-270（directKeys 7 个 + reasoning_details[] + content[] 分片）；
- 独立裸 fetch 四点：经典总结 app.js:5671-5708、UI 模板 :4256-4340、嵌入 :6081-6136（POST /v1/embeddings `{model, input}`）、识图 :3868-3916（走 requestChatCompletion，image_url dataURL）；
- `buildApiEndpoint`（api-utils.js:3-7）：root 补 /v1 再拼 path——仅服务 OpenAI 路径；
- 生图：完全独立 NAI 代理（core-utils.js:936 imageGenBaseUrl；URL 模板 app.js:8444-8463；startGeneratedImageTask :2193-2238 POST /api/jobs + 轮询；终图 imageUrl 直出；设置 UI index.html:1779-1855；imageModels 清单 core-utils.js:1000-1003）；
- 供应商 v1.1.0 代码地图：normalizeUserApiProviders app.js:705-717（**重建对象——新字段必须加映射白名单**）、allApiProviders :729、parseModelRef :738-748、providerModels :3529、rebuildMergedAvailableModels :3530-3548、fetchModelsForProvider :3549-3561（GET /v1/models Bearer）、ensureProviderModelsLoaded :3562-3568、fetchModels :3569-3588、testProviderConnection :3649-3663、collectModelRefsByProvider :3613-3621、resetModelRefsForProvider :3622-3631、watch 同步 865-894、工坊 remap :919-962、settings 字段清单 :611-653、normalizeApiProviderSettings :810-842、启动保存 :1815-1856/加载 :1889-1967。

## 实施分段

### A. 玻璃补全（luzzy-theme.css，零 patch）
统一雾纸：基础面（AI/用户/system 气泡、typing、思考卡外层）`rgba(var(--tw-gray-100)/0.74)` + blur(18px) saturate(1.2)（-webkit-）+ 发丝线 gray-300/.7；暗色同构换暗 token；思考卡 0.86→0.74、is-open 0.80、内部 bg-gray-50 → 0.45；名字 chip 0.82；工具条收编 gray-50/0.6 + blur14；流式加厚 `.chat-view-root:has(.cot-ui.is-live) .msg-bubble-glass` 0.88 + blur8；@supports 实底回退；输入岛不动。变量化：`--luzzy-glass-alpha` 单点可调。

### B. patch 014 · 外观独立页 + 关于页 + 侧栏重排
- 侧栏：高级组 → 外观（selectView('appearance') + itemClass）→ 关于（selectView('about') + itemClass）→ 设置置底；删 open-appearance emit；
- 外观页：index.html 新 v-if 区块（settings-page-header），迁四 custom-select + 主题预览条；删弹窗 2902-2943、设置页入口卡 1934-1946、重复字号下拉 1947-1953；
- 关于页：logo + versionName/Code（LuzzyBridge.getVersion 降级）+ 上游基线 + CC BY-NC 4.0 署名 + GitHub 外链 + CHANGELOG（v-html renderMarkdown）；luzzy-ext.js injectAboutBranding 自动注入（容器 class 含 about）；
- `assets/ext/luzzy-changelog.js`：`window.LuzzyChangelog={md:'…'}`，由 `tools/gen-changelog.mjs` 从 CHANGELOG.md 生成（转义反引号/${）；挂载 tag 在 patch 014 区块；
- app.js：删 showAppearancePanel 及两处引用。

### C. patch 015 · 供应商管理器大扩展
1. 数据模型：apiProviders 条目 + `{protocol:'openai'|'anthropic'|'gemini', models:[{id,label,contextLength,maxOutput,inputModalities[],type,extraBody}], extraBody}`；parseLengthToken（1024000/100K/1M/100k/1m，K=1024 M=1024²）+ formatLengthToken 反向；**normalizeUserApiProviders 白名单保全**（protocol/models/extraBody/label 等）；老商补默认值。
2. 编辑器二级弹窗（z-[60] 叠管理器上）：供应商 id/名称/协议（三选一，URL 占位联动）/URL/Key/供应商级请求体（键值行，值可空=懒编辑）/模型增删改（逐模型行：id、显示id、上下文、最大输出、输入模态三选多、类型三选一、模型级请求体）；热检测预设五组（大小写不敏感长词优先：deepseek-v4-flash-vision-exp > deepseek-v4-flash > glm-5.3-flash > glm-5.3 > deepseek-v4-pro），只填空字段不覆盖已编辑值 + 轻提示 + 一键撤销；改 id → collectModelRefsByProvider 扫全槽位 + key 键重映射（确认后执行）；保存 → 手动模型并入 providerModels + rebuildMergedAvailableModels 热更新；「添加供应商」浮出可见性修复。
3. 三协议适配层（runtime-services.js + app.js）：
   - resolveModelRequest 扩展返回 {protocol, modelMeta, extraBody 合并}；合并序：核心字段 > 模型级 > 商级 > 全局（temperature/reasoning_effort）；
   - openai：现路径 + max_tokens（有才发）+ extraBody 合并；
   - anthropic：POST {base}/v1/messages，headers x-api-key + anthropic-version: 2023-06-01 + anthropic-dangerous-direct-browser-access: true；body max_tokens 必填（缺省 8192）、system 抽出、图片转 source base64、独立 SSE 解析（content_block_delta.text_delta→content、thinking_delta→reasoning）；禁 embedding；
   - gemini：POST {base}/v1beta/models/{id}:streamGenerateContent?alt=sse&key=（非流式 :generateContent）；contents/systemInstruction/generationConfig{temperature,maxOutputTokens}；thinkingBudget 映射；SSE candidates[0].content.parts（part.thought→reasoning）；embeddings batchEmbedContents；
   - fetchModels/检测按协议分型（anthropic GET /v1/models x-api-key；gemini GET /v1beta/models key 参数 + supportedGenerationMethods）；
   - 四个裸 fetch 点（总结/UI模板/嵌入/识图）全走适配层；usage + protocol；
   - 工坊隔离：remap 仅对 protocol==='openai' 生效。
4. max_tokens 注入 + 展示（选择器副标题 `1M · 文本+图像`）。
5. 生图：生图设置加「模型来源」STA1N 官方/自定义（下拉=全商 type==='image' 模型，openai 协议限定）；startCustomImageTask POST {provider}/v1/images/generations `{model,prompt,n,size?,response_format:'b64_json'}`，b64→dataURL 渲染进既有 data-image-request 卡片；settings.imageModelSource 切换联动。

### D. 版本/登记
versionCode 7 / versionName 1.2.0；EXTRACT_VERSION 5→6；patches README 登记 014/015、012/013 交叉引用更新；node --check 全量（app.js/ui-components.js/runtime-services.js/luzzy-changelog.js）。

### E. 验证
1. 模拟器 LuzzyRP_Test：外观页/关于页/侧栏 + 持久化 + 设置页无残留入口；
2. 供应商编辑器端到端（临时 fetch 探针法，v1.1.0 已验证模式）：三协议商新增/编辑/删除、热检测五预设（大小写混排）、槽位热更新、id 重映射、引用回落；
3. 三协议解析：CDP 罐装 SSE 拦截（text/reasoning/vision/usage），验证后彻底清理；openai 真连已有（DeepSeek）；
4. 记忆：gemini embedding 桶回归、anthropic 禁 embedding；
5. 生图：STA1N 回归 + 自定义切换联动；
6. 真机（小米 debug 包）：玻璃四态 adb screencap（**CDP captureScreenshot 遇 blur 挂起，禁用**）、流式性能、核心回归、杀进程数据保留；
7. 设计门控：DESIGN.md 增补（玻璃 v2 token 改写 + 外观页/关于页/编辑器组件）→ 五维 critique + pro-rules → CHANGELOG/README/WORKLOG → assembleRelease → push → Release v1.2.0（附 APK）。

## 风险与对策
- 多 blur 表面流式性能 → alpha/blur 变量化（--luzzy-glass-alpha 单点），掉帧整体上调 0.85+；
- app.js 改动面大 → 集中登记区块、小步提交、裸 id 回落语义不动（老数据零迁移）；
- normalize 丢字段 → 先写保存→重载→字段仍在验证再实施；
- anthropic/gemini 无 key → 罐装 SSE + 降级路径（失败提示不白屏）。

## 默认不做
每模型温度覆盖；自定义 HTTP headers；模型置顶排序；拉取模型黑名单；计费价格字段；视频输入实际发送（字段仅能力预留）；gemini/anthropic 图像模型接生图。