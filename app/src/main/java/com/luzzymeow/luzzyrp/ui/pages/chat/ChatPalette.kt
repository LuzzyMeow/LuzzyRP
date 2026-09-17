package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.ui.graphics.Color

/**
 * 聊天页调色板（v3.2 收敛）——**聊天沉浸形态的特许色区**。
 *
 * ## 为什么存在（与「零硬编码色相」契约的关系）
 *
 * Loom v4 契约规定色相零硬编码（全部从 M3 role 派生），唯一豁免是卡面 monogram。
 * 聊天页是**沉浸形态**：MeshGradient 暖幕、玻璃 tint、白字 scrim 这组颜色是「聊天适配」
 * 的实证配方（历史真机调出来的，KDoc 各处有出处），它们不随 M3 scheme 联动——
 * 把它们塞进 role 反而会破坏全 app 的 token 语义。
 *
 * 处置：**收进围栏而不是散落**。此前这组值分散在 4 个文件（MeshGradientBackground /
 * ChatComponents / ChatPage / BranchListSheet+ChatThinking 重复的魔色），本文件是唯一落点；
 * 聊天体系内新增色一律加在这里并注明出处。其余页面**不得**引用本文件。
 */
object ChatPalette {

    // ── 暖幕（MeshGradientBackground 的底与光斑）────────────────────────

    /** 暗色暖底四段（自上而下渐深）。 */
    val DarkBase0 = Color(0xFF231917)
    val DarkBase1 = Color(0xFF1C1412)
    val DarkBase2 = Color(0xFF171110)
    val DarkBase3 = Color(0xFF140E0D)

    /** 亮色暖底四段（纸白微暖）。 */
    val LightBase0 = Color(0xFFFFF4F1)
    val LightBase1 = Color(0xFFFDEFEA)
    val LightBase2 = Color(0xFFFFF4F1)
    val LightBase3 = Color(0xFFFFF8F6)

    /** 三枚光斑：珊瑚 / 琥珀 / 柔粉（亮暗各一组）。 */
    val BlobCoralDark = Color(0xFF723520)
    val BlobCoralLight = Color(0xFFFFDBD0)
    val BlobAmberDark = Color(0xFF51461A)
    val BlobAmberLight = Color(0xFFF4E2A7)
    val BlobSoftDark = Color(0xFF5D4036)
    val BlobSoftLight = Color(0xFFF7E4DF)

    // ── 玻璃（ChatComponents 实证配方）──────────────────────────────

    /** 玻璃 tint：暗 0xFF3A2E26 / 亮 0xFFF1E3D9（KDoc 实证配方，勿单改一头）。 */
    val GlassTintDark = Color(0xFF3A2E26)
    val GlassTintLight = Color(0xFFF1E3D9)

    /** 玻璃 tint（AI 侧，非用户消息）：暗 0xFF2B2824 / 亮 0xFFF5F0E8（同上，实证配方）。 */
    val GlassNeutralDark = Color(0xFF2B2824)
    val GlassNeutralLight = Color(0xFFF5F0E8)

    // ── 顶栏区 scrim 与白字（ChatPage）────────────────────────────

    /** 消息区滚动时顶栏后的暖黑 scrim 基色。 */
    val ScrimWarm = Color(0xFF141413)

    // ── 二级面板魔色（原两处重复：BranchListSheet / ChatThinking）────

    /** 二级面板的次级文字/边线暖灰（两处原本各自定义同一值，v3.2 合一）。 */
    val PanelMuted = Color(0xFFBEB6A8)

    // ── 卡面 monogram（契约唯一豁免色；原两处字面量收敛于此）────────

    /** 名牌首字的固定深暖底（白字压浅粉/深棕两头都不对的历史结论，DESIGN-ui-v4 §1）。 */
    val MonogramBase = Color(0xFF2E2724)
}