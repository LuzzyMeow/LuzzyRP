# NOTICE · ui/theme/mcu（vendored material-color-utilities）

- 来源：https://github.com/material-foundation/material-color-utilities
  （main 分支快照 commit `5b3618b`，2026-09-12 经 GitHub API tarball vendor）
- 许可：**Apache License 2.0**（本目录 LICENSE 文件为原件全文）——与 AGPL-3.0 并存无冲突，
  **不触发** LuzzyRP 的 AGPL 义务（非 rikkahub 代码，不受其 copyleft 波及）；
- 范围：上游 `kotlin/` 目录全量（hct / dynamiccolor / palettes / scheme / blend /
  contrast / dislike / quantize / score / temperature / utils，43 个 .kt），包名保持
  上游顶层名（`hct` / `dynamiccolor` / …）以对齐 rikkahub 的消费写法；
- 用途：`ui/theme/LuzzyTheme.kt` 以 seed `#CC785C` 生成 TONAL_SPOT 亮暗双 ColorScheme
  （机制参照 rikkahub `CustomTheme.kt`，数值为 LuzzyRP 自有推导，见 DESIGN-compose §2）。