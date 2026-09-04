# LuzzyRP 开屏方向选定记录（huashu-design 三方向硬门 · Gate 文件）

- 日期：2026-09-04（会话 20）
- 任务：开屏启动动画自创设计（需求 1），硬性规定 9 全流程（4 项 SKILL 主文档已复读）
- 三方向设计板（`docs/design/splash-v1/`）：
  - `board-a.html` · **手稿终端**（轮盘 16 号「终端核软未来」，Cursor/Teenage Engineering 血统）
  - `board-b.html` · **开卷 Open the Journal**（现实参照：Aēsop《Aromatique Candles》，
    Awwwards SOTD 2021-01-28 + Webby 2021，多源核实置信度高）
  - `board-c.html` · **落笔成序**（设计师视角：原研哉——留白与墨）

## 用户选定：方向 B「开卷」

> 用户原话（2026-09-04）：「我选择B，但我没看到动画效果，可能是演示有bug，但我很喜欢B的设计理念，
> 现在开始工作 需要注意的是，我们所有的改动内容必须严格做好标记以防止上游更新破坏我们的二创内容，
> 此条为再次提醒」

- 备注：用户未在侧边栏预览中看到动画 = 方向板为单次播放后定格终帧（预览 iframe 加载即播完），
  点板内「重播」可复看；不影响设计本体，落地节拍与方向板一致。
- 落地：patch 027（index.html 开屏 DOM 替换 + ext/luzzy-theme.css 动画层，head 注入链首帧生效）；
  patch 003 重放块退役（标记保留于 index.html 注释，意图由 012-027 实体承载）；EXTRACT 23。