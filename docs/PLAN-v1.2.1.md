# PLAN · LuzzyRP v1.2.1（召回块防合并 · 记忆内容管理器 · 品牌色收编蓝色 · 上游标记体系）

> 2026-09-02 会话 13 定稿并获批。版本 v1.2.0 → v1.2.1，versionCode 7→8，上游基线 RP-Hub 1.8.9 不变。
> 已确认决策：记忆管理=角色选择器查看指定角色；分片编辑=强制重嵌成功才保存；新增上游标记体系（硬性规定 10）。

## 背景（会话 13 排查结论）

用户反馈「对话无记忆工具调用卡片 / 真实上下文请求无记忆分片」。排查+模拟器罐装复现结论：
- 记忆链路（提取→嵌入→量化存储→分桶检索→注入）**全部正常，无 v1.2.0 回归**；
- 「没卡片」= 主动工具默认关（tool_grep/tool_web `enabled:false`）+ 提示词协议需 `<reason:…>` 行 + 检索对象是对话原文而非分片；
- 「没分片」主因 = **查看器标注缺陷**：召回块 role=user 无 `_preventContextMerge`，深度 4 注入点紧邻用户消息时被 `postprocessContextMessages`（data-services.js:703 `mergeConsecutiveRoleMessages`）合并，`buildContextViewerState` 的 `startsWith('<role_memory_vector_recall>')` 判定失效 → 显示为普通 USER 楼层（分片实际已在请求体内，模拟器实测 `similarity="103.0%"` 分片入体）；次因 = 保留窗口（vectorKeepFloors 默认 50 楼防重复）/ 阈值 0.45 硬编码 / 分桶检索失败仅 console.warn。

## 一、召回块防合并（patch 016 · data-services.js 一处）

- `injectContextMessages`（~1025）向量召回 splice 对象加 `_preventContextMerge: true`；
- 链路支持已验证：`toPlainContextMessage` 保留字段（:651）、`mergeConsecutiveRoleMessages` 双侧守卫（:675）；
- 仅召回块；at_depth 世界书不加（标注走 `_worldInfoEntries` 不受合并影响）；
- 登记 tools/patches/README.md（预期冲突点：上游重写 injectContextMessages）。

## 二、记忆内容管理器（patch 017 · index.html + app.js）

**数据层（app.js 新增集中登记区块）**
- 角色选择器：`characters.value` 全列表；选中角色 `readStoryBranchesForCharacter(char)` 取分支；多分支显示分支选择器（默认活跃分支）；scopeId=`getStoryBranchScopeId(char.uuid, branchId)`；
- 懒加载：切角色/分支时 `getScopedStoredValue('memories'/'classic_memories', scopeId)`；当前角色直接用内存 `memories.value`/`classicMemories.value`；分页 LIST_PAGE_SIZE=10 + pagination-controls；
- 统一写路径助手：`writeScopedVectorMemories(scopeId, list)` / `writeScopedClassicMemories(scopeId, list)` —— scopeId==当前 → 原位更新内存数组 + `saveMemoriesNow()`/`saveClassicMemoriesNow()`；否则 `setScopedStoredValue(..., await compactMemoriesForStorageAsync(list))`；
- CRUD：
  - 编辑分片 → 单条重嵌 `requestMemoryEmbeddings([新文本])`（memorySettings.embeddingModel）→ 成功才写回（embedding/embeddingModel/embeddingProvider 更新、contentFingerprint 置 ''、summary=trim(paragraph,900) 重算）；失败 toast 不落盘；
  - 启停分片 enabled 翻转；删除走 confirmAction（无持久化去重缓存需清理）；编辑总结纯文本直写；「清空此角色全部记忆」showVueConfirmModal 列明细；
- 状态 `memoryManager = reactive({ selectedCharId, branchId, loading, vectorList, classicList, vectorPage, classicPage, expandedShardId })`。

**UI 层（index.html 记忆视图新增「记忆内容管理」卡）**
- 顶部：角色选择器（custom-select）+ 分支选择器（多分支才显示）+ 统计徽标；
- 分片行：turn/sequence 徽标、启用开关、两行预览（点开全文）、`[商名] 模型名` 徽标、编辑/删除；
- 总结行：轮次范围、文本预览、编辑/删除；重试仅当前角色（复用 retryClassicMemory）；
- 编辑弹窗 ModalShell z-[60]：分片=段落 textarea + 元信息 + 重嵌提示；总结=summary textarea；
- setup return 追加（10638-10697 区段）。

## 三、品牌色收编蓝色（luzzy-theme.css 零 patch）

品牌 token：亮 500 #CC785C / 600 #A9583E / 700 #8F4732；暗 500 #D97757 / 600 #B85C3E / 700 #E0946F。
- splash（styles.css 硬编码，覆盖不改上游）：`.entry-transition` bg blue-100→gray-100 渐变；sheen rgba(59,130,246,.14)→primary-500/.14；底盘阴影 rgba(37,99,235,.6)→primary-600/.6；`.entry-logo-hub` #2563eb→primary-600、渐变→primary-600→primary-500 两停（去 teal）；下划线→primary-600→primary-500；`.embedded-loading-spinner`→primary-600；
- 设置页两横幅：index.html:1273（from-blue-500/to-indigo-600）、:1892（from-indigo-600/to-violet-700）→ luzzy-theme.css 覆盖 `--tw-gradient-from/to` 4 条规则（!important 惯例）；不改类名（保 classic 原样）；
- data-theme 时机已验证安全（app.js document.write 解析期阻塞执行，splash 期间属性就位）；
- DESIGN.md Colors 章新增规则：禁新增裸 blue/indigo/violet 色相类；上游遗留蓝由 luzzy-theme.css 收编（附清单）。

## 四、上游标记体系（硬性规定 10 + verify 脚本 + 存量补全）

1. HARD_REQUIREMENTS.md 新增第 10 条「改动标记与同步适配」：上游文件二创改动必须带 `[LuzzyRP patch NNN]` 标记注释（JS `//`、HTML `<!-- -->`、CSS `/* */`），016 起新 patch 强制；apply-patches.ps1 为唯一重放通道；同步后必须跑 verify-markers.ps1 全绿；CHANGELOG 声明本条修改；
2. AGENTS.md：§4.2 标记格式规范 + 同步适配清单（重放失败→手工合并→更新重放块→补标记→verify 全绿→WORKLOG）；§4.1 SOP 插入 verify 步骤；§1.5 工具表登记；
3. `tools/verify-markers.ps1`：按登记表校验「文件+标记串+最低出现次数」，逐项 PASS/FAIL；
4. 存量补全审计：app.js×30/index.html×9/ui-components×3/runtime-services×4，core-utils.js（patch 009）零标记 → 补；其余逐 patch 审计补缺，同步更新 apply-patches.ps1 锚点。

## 五、版本资产

build.gradle.kts versionCode 8 / versionName 1.2.1；AssetExtractor.EXTRACT_VERSION 7→8；patch 016/017 登记；CHANGELOG v1.2.1（含硬性规定 10 声明）→ gen-changelog.mjs；README 版本表；node --check 门禁。

## 六、验证

1. 模拟器罐装重放召回场景 → 查看器标注恢复（docs/design/verify-v121-context-label.png）；
2. 记忆管理器：双角色分组切换/懒加载/分页；编辑分片重嵌成功+失败路径；删除/启停/清空；当前角色即时联动；多分支选择；classic 编辑；
3. 颜色：luzzy 亮/暗 splash+横幅 vs **classic 对照保持原蓝**；
4. verify-markers.ps1 自测；常规回归（对话/提取/外观页/杀进程数据保留）。

## 风险

- tailwind CDN JIT gradient 自定义属性覆盖需精确匹配生成结构（实施时 DevTools computed 为准）；
- 编辑重嵌依赖嵌入模型可用（离线不可编辑，toast 明示）；
- patch 017 改动面大，集中在登记区块、小步提交。

## 默认不做

每角色导出导入；孤立 scope 清扫 UI；批量多选删除；向量检索器改造；分片 turn=0 边角。
