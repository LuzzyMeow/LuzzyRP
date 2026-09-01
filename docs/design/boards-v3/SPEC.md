# boards-v3 共享 SPEC · 「暖幕手记 × 液态玻璃」三方向板

> 本文件是三个方向板 subagent 的**唯一共同输入**（huashu-design Fallback Phase 3）。
> 三个 subagent 各只读本文件 + 自己的方向简报，互不参考。

## 任务

为 LuzzyRP「暖幕手记」主题设计**液态玻璃 / 半透明容器增强**的方向板（direction board）。
前提：**不推翻现有 Claude token 体系**（cream 画布 + coral accent + Lora 衬线名牌），
在其上融入 液态玻璃（Apple Liquid Glass）与 半透明 Windows（Fluent Mica/Acrylic）容器语言。

## 产品与场景

- LuzzyRP：移动端 AI 角色扮演 App（Android WebView 壳 + Vue 前端）；
- 使用场景：手机竖屏沉浸阅读长对话、夜间暗纸模式、中端安卓机（性能敏感）；
- 每次对话，都像一本有你的小说——文学、温暖、手作。

## 输出格式（三版必须统一，便于横向对比）

每方向 **1 个单文件 HTML 方向板** + 1 张截图：

- 视口 **1440×900**（`npx playwright screenshot file:///<板>.html <板>.png --viewport-size=1440,900`）；
- 板面结构：深色中性背景（#141413）上左右并排**两个手机框**（各 ~340×700，圆角 36px），左=亮色模式、右=暗色模式，渲染**同一个 RP-Hub 聊天场景**；
- 聊天场景必含（证明玻璃的「透」）：
  1. **悬浮顶栏**：聊天内容延伸到顶栏后方且被模糊（顶栏玻璃悬浮于已滚动内容上）；
  2. **居中模态弹窗**：玻璃弹窗悬浮在聊天上方（如「界面主题」设置卡）；
  3. **输入岛**：圆角 22px 输入条 + coral 圆形发送键；
  4. AI 气泡（含 Lora 衬线角色名牌「Luna」）与用户气泡若干、一条分割时间戳；
- 板底部：**玻璃配方数值表**（blur / tint / alpha / border / specular，亮暗各一列）+ 一句气质定位 + 参照作品名；
- 色板条：本方向用到的关键色块（含色值标注）。

## 强制约束

- **色值只许来自本 spec**（即 DESIGN.md Claude token），禁止发明新色相：
  - 亮：canvas `#FAF9F5` / surface-soft `#F5F0E8` / surface-card `#EFE9DE` / hairline `#E6DFD8` / muted `#6C6A64` / body `#3D3A36` / ink `#141413` / coral 图形 `#CC785C` / coral 按钮底 `#A9583E` / coral-100 `#F5E2D8` / coral-300 `#E5B099` / 用户气泡底 `#F1E3D9` / highlight `#F5D9A8`；
  - 暗：canvas `#171614` / surface-soft `#201E1B` / surface-card `#2B2824` / hairline `#3E3A34` / muted `#A5A198` / body `#DED9CF` / on-dark `#FAF9F5` / coral 图形 `#D97757` / coral 按钮底 `#B85C3E` / coral-50 `#2E211B` / coral-700 文字 `#E0946F`；
  - 玻璃 tint / alpha 的调整只允许在上述色上做透明度与混合派生，不得引入新色相（禁紫/禁青/禁霓虹）；
- **文字对比度 ≥4.5:1**：落在玻璃上的文字，其背后有效底色必须足够实（提高该区域 alpha 或文字区另加实底），板上需自检；
- 字体：角色名牌/区块标题 = **Lora**（衬线）；正文/UI = **Alibaba PuHuiTi 3.0** + AlibabaSans；
  字体文件在 `D:\.NekoTool\LuzzyRP\app\src\main\assets\fonts\`（Lora-Regular/Italic、AlibabaPuHuiTi-3-55-Regular/65-Medium/85-Bold、AlibabaSans-Regular/Medium/Bold 等 .woff2），
  **拷贝到 `docs/design/boards-v3/fonts/` 并以相对路径 @font-face**（三版共用同一份）；
- 手作记号克制（全屏 ≤3 处）；**禁止**：emoji 图标、紫渐变、左彩边圆角卡、霓虹 glow；
- 工程备注（写进配方表下一行）：backdrop-filter 仅用于固定/悬浮层；滚动长列表逐气泡 blur 为重方案需标注代价；`@supports` 降级 = 实底。

## 三方向定义（互异 = 玻璃的面积与强度不同，不是换皮）

各 subagent 只做自己的方向，按以下简报执行：

### 方向 A「雾纸 Frost-Paper」（保守 · Windows Mica/Acrylic 派）
- 玻璃只用于**固定 chrome**：顶栏、输入岛、抽屉、模态弹窗；聊天气泡维持现有纸感（不透）；
- 高不透明度 ~85-92% + 中度 blur 14-18px + 发丝线 1px 边；玻璃几乎不抢戏，像「毛玻璃窗台上的手账」；
- 参照：Windows 11 Mica / Arc 浏览器侧栏。

### 方向 B「琥珀琉璃 Amber-Glass」（居中 · 暖 tint 派）
- 玻璃带**暖色 tint**（cream/coral 派生的暖白/暖黑半透），alpha ~72-82%，blur 12-16px；
- 顶边 1px 内高光（inset specular）；**AI 气泡也玻璃化**；
- 气质：「阳光穿过琥珀」；参照：macOS 窗口 tinted glass / Claude 移动端弹层。

### 方向 C「晨露 Liquid-Clear」（激进 · Apple Liquid Glass 派）
- 低 blur 8-12px + 高透 alpha ~55-68% + `backdrop saturate(1.4-1.8)` 补偿；
- 更强 specular 边缘高光与内侧光影（水滴感）；**用户气泡也玻璃化**；
- 气质：「雨后叶尖的露水」；参照：Apple iOS 26 Liquid Glass / visionOS。

## 交付

- 文件：`docs/design/boards-v3/direction-{a-frost-paper|b-amber-glass|c-liquid-clear}.html` + 同名 `.png`；
- 自检：截图后自己看一眼——玻璃的「透」在顶栏与弹窗处清晰可辨、文字无对比度事故、亮暗两框气质一致。
