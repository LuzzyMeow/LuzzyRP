## LuzzyRP v1.5.1 — 流式结尾丢字 · 增强记忆嵌入整链不可用 · 返回键语义

> 上游基线 RP-Hub **1.9.8**（`53a8d80`）· versionCode 19 · 稳定版（附 APK）

**缺陷修复版，无新功能。** 三处修复均由一次全量静态检查抓出（ESLint bug 类规则集
+ 真实浏览器 Vue 模板编译 + 全量语法/内联脚本），每处都带**负控验证**——
先证明检查会响，再证明修复后不再响。

### 修复

**1. 流式结尾丢字（严重）**

`commitLiveDelta` 在 `finally` 中**恒抛 `ReferenceError`**。

流式收尾时会强制把「活通道」缓冲区追平 —— 最后一段正文靠这一步落盘。但该函数的
`const` 声明当时写在 `try {` **之内**：块级作用域使它在 `finally` 里不可见，那行调用恒抛错，
又被自身的 `try/catch` 吞掉（注释写着「绝不阻断收尾」，于是**静默失败**）。

**后果**：流式结束时不追平缓冲区，**最后一段正文丢失** —— 表现为回复尾部被截断，
且没有任何报错可循。

修法：把活通道状态与 `commitLiveDelta` 提到 `try` 之外的函数体作用域。

> 负控实测：把声明改名后，一次 5 帧流式只落库首帧 `"动态"`，其余四帧全部丢失；
> 修复后完整落库 `"动态检查：活通道收尾追平验收句。"`

**2. 增强记忆的向量嵌入整链不可用（严重）**

`requestMemoryEmbeddings` 里的 `embeddingResolved` 在函数体中被引用 **9 次却从未声明**
（1.9.5 三方合并时该声明整段丢失）。每次嵌入调用都在首行抛 `ReferenceError`，于是：

- 总结记忆的**分片补录**必然失败
- **手动向量检索**必然失败
- 查询向量**现算**必然失败

且根因被「向量检索失败」toast 掩盖 —— 用户只看到检索不出结果，看不到为什么。

修法：按 `requestClassicMemoryCompletion` 的既有口径补回 `resolveModelRequest(model)`
解析（url / apiKey / 裸模型 id / protocol / providerId），使嵌入请求也走多商路由。

**3. 返回键语义缺口**

上游 RP-Hub 是**不用 History API 的单页应用**（全仓无 `pushState` / `popstate` /
`hashchange`），页面切换只改响应式 `currentView`，于是 `webView.canGoBack()` **恒为 false**：

> 在设置 / 关于 / 外观 / 记忆 / 预设 / 世界书等**任意非对话页按返回键，都会直接退出应用**，
> 而不是回到对话页。

这偏离了本项目既有的「非对话页 → 回对话页；否则退出」返回语义，也偏离安卓用户预期。

修法（patch 053，扩展层零上游逻辑改动）：

- 新增 `ext/luzzy-back.js`，提供 `window.__luzzyHandleBack()`
- `MainActivity` 返回键改为「**先问页面是否消费 → 再走 WebView 历史 → 最后退出**」
- 接管链由内到外：弹窗（模型编辑器 → 供应商编辑器 → 供应商管理器 → 其它已知弹层）
  → AppNavigation 抽屉 → 回对话页
- manifest 显式开启 `android:enableOnBackInvokedCallback`
  （Android 13+ 默认走 OnBackInvokedCallback 通道，未开启时系统会告警并可能绕过 dispatcher）

> 负控实测（摘掉挂载行）：设置页按返回 → 直接回到桌面；
> 修复后 → 回到对话页且应用存活

### 注意事项

- **单 APK、同一密钥库**（`apksigner` 指纹 `ed78235d…ffb1` 未变），已装 v1.5.0 可覆盖升级
- 上游基线仍为 **RP-Hub 1.9.8**（本版未动上游文件，仅按登记 patch 053 新增一处挂载行）
- 回归门：`verify-markers.ps1` **69 PASS / 0 FAIL**；`tools/` 下 5 个 JS 门禁全 PASS；
  `:app:check` + `:app:lintRelease` BUILD SUCCESSFUL；模拟器实测返回键三级链
  （设置页 → 对话页 → 桌面）
