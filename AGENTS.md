# AGENTS.md · LuzzyRP 开发指南（v1.5.0）

> 接手本仓库的 Agent：先读完本文件再动手。冲突时以用户当前指示为最高优先级。

## 1. 项目一句话

LuzzyRP = **原生 Kotlin 壳 + WebView 承载上游 RP-Hub（Vue 3）**，launcher 为 `MainActivity`。
界面与业务全部在 `app/src/main/assets/rphub`（上游）+ `assets/ext`（扩展层）里跑，原生侧只剩壳。
上游同步**持续进行**（**当前基线 RP-Hub 1.9.8，已与上游 main 对齐**；1.9.3→1.9.8 的
合并台账见 `docs/PLAN-upstream-merge.md`）；遵循上游协议
（`LICENSE` / `LICENSE-AGPL-3.0` / `docs/LICENSING.md`）。仅侧载分发，不上架。

> **已放弃的路线（2026-09-20，用户拍板）**：v2.0 的原生 Kotlin 聊天传输层与 v3.0 的
> Jetpack Compose 界面（含 Room 数据层与数据迁移）**已整体删除**，相关文档与产物一并清除。
> 动因：原生界面要与上游 Vue 功能长期对齐 = 把上游每次更新翻译两遍；回到 WebView 路线
> 可直接复用上游实现，同步一次即全量到手。**不要再把原生界面方案重新提出来当默认方向**，
> 除非用户明确要求。详见 `CHANGELOG.md` v1.5.0 段与 `docs/WORKLOG.md`。

## 2. 强制 SKILL 阅读（先读后做，落读取回执）

任何涉及 **UI / 设计 / 动效 / 前端页面 / 交互** 的任务，或**写后端 / 通用代码**的任务，
动手前必须完整读到以下 SKILL 的**正文**（首页摘要不算；先本机后云端，失效按 ①上报用户 ②再找替代）：

| 组 | 条目（读什么） |
|---|---|
| 设计基线 4（全读） | `huashu-design/SKILL.md` · `awesome-design-md/README.md` · `open-design/AGENTS.md` · `ui-ux-pro-max-skill/CLAUDE.md + README.md` |
| 后端 / 通用编码 3（全读） | `DietrichGebert/ponytail`（正文 + 阶梯纪律）· `github/spec-kit`（正文）· `mattpocock/skills`（正文） |

- 本机来源优先：`docs/skills/**`（huashu / awesome-design-md / open-design / ui-ux-pro-max 已存档）；
  其余从 GitHub raw 抓正文（AnySearch `extract` / `web_fetch`，GitHub 页面抓不到就抓 `api.github.com` 目录再取 `raw` 链接）。
- 回执格式：分段列表，每条写「读到正文 ✅ + 关键提取一句话」。读满才许写代码。

## 3. 设计与构建纪律

- **界面真源 = 上游 RP-Hub 自身的样式体系**（`assets/css/styles.css` + `vendor/`），
  本项目的外观改动一律走 `assets/ext/luzzy-theme.css` 与登记 patch，**不引入第二套设计系统**。
- 主题切换由 `data-app-theme` / `data-app-font` 驱动；字体本地打包（`assets/rphub/assets/fonts/` + `local-fonts.css`），禁 CDN。
- 构建：`./gradlew :app:assembleRelease` → 单 APK（`splits.abi` 已注释，禁止恢复）；签名永远同一密钥库（`keystore.properties` 不入库；换签 = 老用户无法覆盖升级），发布前 `apksigner verify --print-certs` 核对指纹 `ed78235d…ffb1`。
- 版本：versionCode/versionName 递增；`CHANGELOG.md` 新增一段（构建期 `genChangelog` 自动同步 README 与关于页数据）。

## 4. 上游同步纪律（本项目核心能力，不可荒废）

- **上游文件只允许通过 `tools/patches/` 登记的 patch 修改**；新功能优先落在 `assets/ext/` 独立文件（零冲突）。
- 同步流程：`tools/sync-upstream.ps1`（覆盖上游 + 重放 patch + 更新指纹）→ 回归实测 → 构建发布。
- **`nsfw_rules`（`built-in-content.js` 内）永远不可触碰**（硬性规定 1）；由 `verify-markers.ps1`
  的 **D1 项**（块 SHA-256 固定）机器校验。
- 参考克隆 `rp-hub-reference/`（不入库）是同步的基线来源，改上游前先 `git fetch` 对齐版本。
  **它的 remote 必须是 SSH 形式**（`git@github.com:STA1N156/RP-Hub.git`）——本机 443 不通，
  HTTPS 必然 fetch 失败；`sync-upstream.ps1` 会在启动时检查并给出改法。
- **当前进度与逐处冲突裁决记录**：`docs/PLAN-upstream-merge.md`（分期合并台账）。
- **patch 登记总表**（live / retired / superseded / never）：`tools/patches/README.md` 文末。
  新增 patch 必须同步登记——它替代了旧的 158 项 needle 清单。

### 4.1 patch 重放失败的处置（上游大改时必读）

实体 patch（`tools/patches/entities/*.patch`）带**期望前像**（头部 `index <pre>..<post>`）。
`apply-patches.ps1` 用「目标文件 LF 归一 blob id == pre」判定能否干净应用；不符则退回
「与上游纯净基线比对」，两者都不符即 **FAIL**——含义是**上游改了同一片区域**。
该脚本**有退出码契约**（0 = 通过；1 = 有 FAIL），`sync-upstream.ps1` 依赖它判定同步成败。

**判定「要重做多少」——跑只读预检，不要手工比对**：

```powershell
git -C rp-hub-reference fetch            # remote 已是 SSH
git -C rp-hub-reference checkout <新版本 ref>
powershell tools/apply-patches.ps1 -CheckBaseline <新ref>
#   退出码 0 = 全部可重放 → 直接跑 tools/sync-upstream.ps1
#   退出码 2 = 有需三方合并 → 走下面的六步
```

> ⚠ **不要**按旧文写的「比对实体头 `index <pre>` 与 `git rev-parse <ref>:<file>`」——
> 参考克隆按 `core.autocrlf=true` 检出，存储 blob 是 **CRLF**，而实体前像是 **LF 归一**后的
> blob id，两者永不相等。照那条做会把**可干净重放**的实体全部误判为需三方合并
> （2026-09-21 实测：1.9.3 → 1.9.7 的 8 枚全被误判）。`-CheckBaseline` 内部做的是
> 「取该 ref 文件字节 → LF 归一 → 算 blob id → 比前像」，这才是正确口径。

**上游改动与 patch 面重叠时，禁止盲目覆盖 + 重放**（会得到破碎树）。正确路线是**三方合并**：

1. **备份当前工作树**（它是「我们的版本」，含全部 patch）；
2. 取三方：`base` = 我们 patch 的上游基线（见 `tools/upstream-fingerprints.txt` 表头的 commit）、
   `ours` = 当前工作树、`theirs` = 新版上游；
3. 逐文件合并：以 `theirs` 为底，把 `ours` 相对 `base` 的**意图**重新落到新结构上
   （不是文本搬运——上游重构后行号与结构都会变）；
4. **重新生成实体 patch**：按 `tools/patches/README.md` §2 生成规程逐枚重生成
   （注意：不加 `--ignore-cr-at-eol`、用同构 base/ 与 ours/ 目录、diff 输出走 cmd 重定向），
   头部前像即为新基线 blob；
5. 更新 `tools/upstream-fingerprints.txt`（表头 `commit <sha>` 必须同步，它是兜底判定依据；
   正常同步由 `sync-upstream.ps1` 自动写，手工合并后才需自己跑一次）；
6. 复跑 `tools/verify-markers.ps1` 与 `tools/` 下的 JS 门禁，再做真机目视。

### 4.2 同步机制的两条硬底线（2026-09-21 加固）

改 `sync-upstream.ps1` 前务必理解这两条——它们各自对应一个**已被修复的真实缺陷**：

1. **清单驱动，绝不整目录删拷**。同步只写「上游 `git ls-files` 列出的文件」、
   只删「旧清单有而新清单没有的路径」。旧实现是「顶层目录先删后拷」，而
   `$excludeDirs=@('vendor','fonts')` 在参考克隆顶层**永远匹配不到任何东西**
   ——于是 `assets/` 被整目录删除，连带吃掉 `assets/fonts/` 下 11 个字体（16.2 MB），
   且字体没有备份。脚本会在同步后断言「两集之外的文件一字未动」（保护不变式）。
2. **覆盖必须 LF 归一写入，不能直接 `Copy-Item`**。上游存储 blob 自带 CRLF
   （该仓库无 `.gitattributes`），直接拷参考克隆工作树会把 CRLF 灌进我方 LF 归一的树，
   一次污染 21 个文件。文本文件一律「读 → LF 归一 → 写」。

**每次同步后必须复跑**：`verify-markers.ps1`（67 项：实体后像等值 / 上游纯净等值 /
语义锚点 / 红线与二创资产）+ `tools/` 下 5 个 JS 门禁。任一不过即同步未完成。


## 5. 测试与验收

- **回归门在 `tools/` 下的 JS 门禁**（原生测试座已随 Compose 路线删除）：
  `node tools/prefix-cache-test.cjs`（前缀缓存）/ `stream-render-test.cjs`（流式渲染）/
  `page-handoff-test.cjs`（转场）/ `model-list-test.cjs`（模型列表）/ `desktop-smoke.cjs`（冒烟）；
  `powershell tools/verify-markers.ps1` 校验二创标记与上游完整性（67 项，见 §4.2）。
- **真机只装 release 包做人工目视**，严禁安装测试件。
- **insets 只信真窗口截图**：`adb shell screencap -p` + `adb pull` 逐页看图——
  「顶栏被状态栏压住 / 文案出屏 / 底部按钮贴导航栏」这类缺陷只有真窗口截图看得见。
- 模拟器：本机 AVD `LuzzyRP_Test` 必须 `hw.ramSize=4096M`、`hw.gpu.enabled=no`（唯一能启动的组合）。
- 构建通过 ≠ 验收通过；`assembleRelease` 成功只说明能编译，界面必须亲眼看（§14.1 视觉验收）。

## 6. 文档落点

- 工作日志 → `docs/WORKLOG.md`（每次会话追加「日期/完成/决策/遗留」，这是跨会话连续记忆）。
- 版本记录 → `CHANGELOG.md`（构建期自动同步到应用内）。
- 过期文档直接删（git 历史可回溯），不搬进 `docs/archive`；
  `docs/skills/**`（必读技能存档）与 `docs/design/brand-logo-v2-source.png`（图标源图）**永不删除**。

<!-- aoci:begin -->
## AOCI 仓库认知

AOCI 为本仓库维护一个稳定、可版本化、可增量更新的仓库级认知层，供模型跨任务复用对系统的理解。

`aoci.txt` 是面向模型的结构化认知索引。它以每个受管理文件、数据库表或其他受管理对象一条独立 Entry 的方式，用符号标签与 F/R/A/S 语义表达对象的核心职责、重要关系、对外契约，以及理解或修改系统时必须知道的非显然约束和设计决策。

Header、目录段和全部 Entry 共同组成完整仓库索引，可以覆盖前端、后端、配置、数据库结构及其他受管理内容。受管理内容发生变化时，通常只需维护受影响的认知条目，不需要重新生成整个索引。

AOCI 提供系统架构、对象职责、重要关系、对外契约和关键约束的高密度视图。

### 工作原理

AOCI 采用“模型生成、模型读取”的认知闭环。

Header、Entry 和 Curation 语义的创作只按当前机器签发的 Plan 与实时 Guide 执行；由 Host 模型基于当前绑定证据独立完成。

Entry 的语义必须来自模型对真实证据的理解。不得仅依据路径、文件名、扩展名、AST、符号列表、依赖扫描、正则、固定模板或规则引擎推导、预填、拼接或改写索引语义。

对 Fresh Bootstrap，只按当前机器签发的 Plan 和实时 Guide 执行。当它们要求创作时，Host 模型创作 Root、Meta、Tag 和 F/R/A/S，提供 authoring-run 声明，并把它绑定到 Plan、Evidence 与完整 Candidate。不得要求 AOCI 填写 `origin=host_model`、制造 Receipt 或把程序生成的 Framework 当作语义。本文件不自行重建 Onboarding 流程。内部批次不是用户决策；只有遇到既有批准边界或真实的安全、漂移、CAS、Recovery 条件才停止。

### 最小使用入口

- `aoci_rules`：取得当前AOCI版本的会话运行合同。
- `aoci_overview`：建立或恢复本仓库的完整认知。
- `aoci_maintain`：受管理对象达到最终稳定状态后检查认知是否需要维护。
- `aoci_update_entry`：提交与当前证据和源码摘要绑定的完整语义更新批次。
- `aoci_report`：仅当当前布局和工具状态支持时，在证据不足、无法可靠生成语义时登记待办，不猜写。

其他MCP工具、CLI命令、参数和专项流程，以当前工具说明、Guide和 `--help` 返回内容为准，不在本文件中重复完整手册。

本区块只规定仓库接入、认知使用和收尾原则。`aoci_rules` 承载当前会话合同，Guide实时输出承载当前Plan的执行顺序与停点，工具Schema、Spec和Validator承载机器结构与判据；Prompt、Description、README和静态文档不能覆盖这些机器事实。

### 建立、生成和恢复认知

1. 每个新的 Agent Run 开始时，应先判断：

   - 本仓库是否已经存在可用的完整AOCI索引；
   - 当前上下文中是否已有与本仓库根、当前索引版本和当前AOCI服务相匹配，并且模型仍可可靠使用的完整仓库认知。

2. 仓库已经存在可用的完整索引，但当前Run没有可靠完整认知时，先调用 `aoci_rules`，再调用 `aoci_overview`。

   完整认知仍可靠时直接复用。局部不确定本身不要求机械重读系统全貌。

   本Run从已知Host上下文压缩恢复时（包括宿主注入的压缩摘要），必须把此前模型认知视为不可靠。压缩handoff不得保留或摘要正式Whole-Index，也不得保留或摘要任何Overview Header、Entry、Chunk、Challenge或Attestation正文；只能保留安全续接所需的receipt身份、未完成write或Recovery状态，以及立即重载指令。复制进handoff的Whole-Index语义或receipt不能证明恢复后模型的当前认知可靠。若当前上下文已无法可靠保留运行合同，先调用 `aoci_rules`。继续业务任务前，使用 `refresh_reasons=["context_compaction"]` 和新的 `refresh_event_id` 调用普通完整Whole-Index `aoci_overview`（不设置 `check_only` 或设为false）；不得使用 `check_only` 或认知probe。原样跟随每个 `next_cursor` 直到 `completed=true`，确认交付，并且只基于新交付正文提交一次Attestation。完成这次新的完整传输后，即使Attestation为partial或fail也消费该generation，并按既有合同继续source-bound任务，不再自动调用第二次Overview。

   AOCI可以针对 `context_compaction`、项目 `cognition_refresh_threshold` 下的机器 `semantic_threshold` 或主要 `phase_transition` 提供checkpoint与认知状态事实。只需要这些紧凑事实时使用 `check_only=true`；这些事实只向Agent提供建议，不替模型决定是否需要系统全貌。

   Agent显式调用普通 `aoci_overview`（未设置 `check_only` 或为false）时，只要能形成一致的CognitionSet，AOCI必须完整交付请求scope。不得因为已有receipt、阈值未达到或没有待处理刷新原因而抑制正文。正式认知Dirty或Stale时仍交付正文，但必须标记不可靠。存在未决恢复或无法形成一致snapshot时失败关闭，不返回混合正文。

   普通Overview返回 `continuation_required=true` 时，必须原样提交 `next_cursor` 并自动继续到 `completed=true`。不得询问用户、开始业务任务或给出阶段性系统结论。Host截断、缺块、重复、乱序、cursor失败、Index变化或`chunk_tokens`变化时停止本次认知链。Attestation完成前不得用Memory、源码、Spec、`aoci.txt`、历史会话、scope、search或Entry读取修补或补充Whole-Index认知。Challenge ordinal是正式Entry序列中的1-based位置；Header内容、注释、空行、Section/Overview/Chunk Marker、Receipt与Metadata均不计数，Chunk Receipt ordinal使用同一序列。Attestation必须原样回绑本次Challenge发布的当前`index_sha256`、`entry_sequence_sha256`与`entry_count`；旧Index、旧Entry序列、旧数量或旧Attestation均无效。完整链结束后只正式提交一次既有模型认知Attestation；同一响应只允许一次不改变语义答案的JSON Schema或字段格式修正。对象、Tag或F不匹配即失败且认知吸收不确定，不得语义重试或旁路补答。首次认知失败时还不得执行Root/Meta、Migration、全局布局或其他未重新绑定的系统级决策。上下文压缩刷新若传输完整、认知身份不变、治理对齐且没有Recovery或第三方冲突，即使Attestation为partial或fail也消耗该refresh generation，并继续原任务，不再自动重读Overview。`system_mastery_percent`只自评系统框架——架构、职责、强关系、稳定外部契约以及高熵安全和维护约束——不表示完整实现或运行实况知识；机器索引覆盖率必须分开。默认只向用户输出由本次真实覆盖率、Challenge、块数、Token和掌握度生成的规定成功或失败一句话。Host截断时提示用户把 `overview_delivery.chunk_tokens` 设置为更小的合法值后重新开始，不得自动修改。

   加法认知等级必须与严格证明字段分开解释。`delivery_verified`表示已加载Index且Host交付已确认，但完整认知验证仍未完成；应表达为“已加载且交付已验证”，不得描述为“没有认知”或“没有理解系统”。`cognition_verified`要求Attestation通过（Challenge至少80%的ordinal完全正确且对象身份至多失手一处），`cognition_governed`还要求治理对齐。通用完整读取失败句只用于真实交付故障。

   当Overview响应包含可选`cognition-state/v2`投影时，必须分别解释各维度。其Level止于`model_cognition_usable`；`strict_attestation_verified`、`governance_aligned`与`current_system_cognition_reliable`都是独立状态，绝不参与该Level。ordinal、对象身份、Tag或核心F不匹配可以导致严格Attestation失败，而模型认知仍然可用；不得仅凭这种不匹配就宣称模型没有理解系统。只有`current_system_cognition_reliable=true`允许无保留地声称当前完整系统认知可靠。投影缺失时继续使用上述Legacy解释。

   普通的只读审计、分析、检查、不修改代码或不提交、不push，不自动等于严格零写入，也不改变上述认知有效性判断。Codex Memory和历史Skill只能辅助恢复经验、用户偏好与调查方向，不能替代与当前仓库根、索引摘要、AOCI服务身份和认知范围匹配的当前认知收据；项目AGENTS和当前AOCI身份在AOCI状态上优先于历史Memory。

   只有用户明确禁止Ledger、元数据、`.aoci`运行资产及任何文件写入时，才按严格零写入处理。若必要的认知建立与该边界冲突，必须报告冲突并请求用户裁决或建议使用隔离副本，不得静默以Memory替代当前仓库认知。

3. 仓库没有可用的完整索引，或当前只有最小骨架、Header不完整、Entries未完成、必要Curation尚未裁决时，如果需要建立正式完整AOCI索引，先取得 `aoci_rules`，然后进入当前AOCI Guide。由Guide依据仓库真实状态决定下一阶段并完成必要安全步骤。

   `aoci_maintain` 不替代索引建立流程。

   不在本文件中自行重建或硬编码完整索引生成状态机。

4. 在长程任务中，模型负责保留当前认知收据并正确使用刷新门禁：

   - Host报告上下文压缩或模型已知系统全貌丢失时，执行上述强制 `context_compaction` 重载规则；AOCI不能自行推断Host事件；
   - 进入真正的主要阶段时声明 `phase_transition`，不得把函数、测试运行或小步骤当作阶段；
   - 在有用的稳定检查点通过 `check_only=true` 取得机器语义计数；
   - 除已知压缩的强制重载外，由Agent判断当前任务是否需要再次显式获取指定scope或完整Overview；
   - 在维护和对齐完成前，保留AOCI报告的Dirty或Stale可靠性状态。

### 任务收尾与认知维护

5. 纯只读问答、分析、版本核验，或没有产生受AOCI管理对象变化的任务，不需要调用维护工具。当前AOCI版本是任意`aoci_overview` check_only或`aoci_maintain`响应里的`cognition_receipt.mcp_service_version`；二进制路径是项目`.mcp.json`里的`command`，CLI不必在PATH上。

6. 发生受AOCI管理对象变化时，待其达到本次任务的最终稳定状态后，只调用一次 `aoci_maintain`。不要在每次中间修改后逐文件维护。

7. 若维护结果返回真实语义候选，Host 模型必须基于每个候选绑定的对象和必要证据，独立创作完整标签与F/R/A/S更新。通过 `aoci_update_entry` 一次提交当前机器签发批次的完整候选集合，同时原样保留每项 `source_sha256`、`candidate_id` 与对应domain批次身份。`max_entries`只限制单次请求和原子事务，不限制logical plan、Whole-Index或Managed Scope。`remaining`非零时，在当前批次成功Apply后重新调用Maintain并从新preimage继续；绝不能为满足transport上限缩减Index覆盖或自行截取返回批次。

   没有足够证据且当前布局支持 `aoci_report` 时，使用它而不猜测、套用模板或为消除待办而生成缺乏证据的认知。

8. 必须遵守工具返回的结构化状态和安全边界：

   - `repair_required`：只修复明确命中的候选，再重新提交当前机器签发的完整批次；
   - `stopped`：结束当前写入尝试并检查 `failed_step`、错误、正式写入证据与Recovery。auto模式下，已证明零写入则记录closure并重新Plan；完整Intent和可证明postimage则Resume；策略要求Rollback且preimage可证明则精确恢复后重新Plan。只有证据不足、第三方正式字节冲突、需要审批或外部动作，或命中其他真实安全边界时，才停止整个用户任务；
   - 冲突、审批、人工裁决、权限和安全信号不得忽略；
   - 已经对齐后不得重复维护或重复写入；`refresh_ready_for_overview` 是checkpoint事实，由Agent决定是否为下一阶段请求普通完整Overview。

   维护完成后如果又修改了任何受管理对象，之前的维护结果失效，应在新的最终稳定状态重新完成收尾。

9. 用户只限制业务文件范围，但没有明确禁止仓库托管资产时，AOCI托管资产可以在收尾阶段为保持认知一致而更新，并应在审计和提交中与业务文件区分。

   用户明确禁止修改 `aoci.txt`、`.aoci`、元数据或任何额外文件时，以用户限制为准，不得写入，并如实报告剩余不一致。

### 专项流程

初始化、完整索引生成、Header生成、Entries生成、数据库结构索引、Curation、人工评审和故障恢复，只按当前AOCI Guide或工具在对应阶段返回的指令、命令和安全停点执行。

不预加载、不猜测，也不自行重建这些专项流程。平台调用方式、请求格式、批次上限、审批规则、索引格式细节和恢复步骤由对应Guide、工具说明、模型Prompt和CLI帮助按需提供。
<!-- aoci:end -->
