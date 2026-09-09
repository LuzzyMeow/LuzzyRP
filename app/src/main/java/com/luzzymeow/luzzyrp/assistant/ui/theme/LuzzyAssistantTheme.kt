package com.luzzymeow.luzzyrp.assistant.ui.theme

import android.content.res.AssetManager
import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 助手页主题（v1.5.0）。
 *
 * **设计真源**：仓库根 `DESIGN.md`（唯一设计契约）。本文件只做「token 落地」——
 * 数值逐项取自 DESIGN.md 的 Colors / Typography / Layout & Elevation & Shapes 三章，
 * **不得临场发明颜色或圆角**（硬性规定 9 第 3 步）。
 *
 * 主题恒定「暖幕手记 × Claude」（v1.2.3 patch 028 起单轨化），亮/暗双模式由
 * [darkTheme] 决定；跟随 Web 端主题（DataStore 只读同步，见 PLAN §4.3）。
 *
 * 字体（硬性规定 4）：display = Lora（本地打包），正文 = AlibabaSans + Alibaba PuHuiTi 3.0。
 * 上游资产为 woff2（Web 用），Android Compose 需 TTF——构建期由
 * `tools/assistant-fonts.mjs` 从 `assets/rphub/fonts/` 转换并落 `assets/assistant/fonts/`，
 * 转换失败时回退系统衬线/无衬线（见 [LuzzyTypography] 注释），**禁止运行时 CDN**。
 */
@Immutable
data class LuzzyColors(
    // 表面阶梯（DESIGN.md「Colors」章）
    val canvas: Color,
    val surfaceSoft: Color,
    val surfaceCard: Color,
    /** 卡片面：上游 `bg-white`（暗色被 luzzy-theme.css 覆盖为 gray-100 #201E1B）。 */
    val card: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    // 文字阶梯
    val ink: Color,
    val body: Color,
    val bodyStrongMid: Color,
    val muted: Color,
    val mutedSoft: Color,
    // 品牌 accent
    val accentGraphic: Color,
    val accentButton: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    // 语义
    val highlight: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
) {
    val isDark: Boolean get() = canvas.luminanceIsDark()
}

private fun Color.luminanceIsDark(): Boolean =
    (0.2126 * red + 0.7152 * green + 0.0722 * blue) < 0.5

/** 亮色（DESIGN.md「Luzzy 暖幕手记 · 亮色」表）。 */
val LuzzyColorsLight = LuzzyColors(
    canvas = Color(0xFFFAF9F5),
    surfaceSoft = Color(0xFFF5F0E8),
    surfaceCard = Color(0xFFEFE9DE),
    card = Color(0xFFFFFFFF),
    hairline = Color(0xFFE6DFD8),
    hairlineStrong = Color(0xFFBEB6A8),
    ink = Color(0xFF141413),
    body = Color(0xFF3D3A36),
    bodyStrongMid = Color(0xFF52504A),
    muted = Color(0xFF6C6A64),
    mutedSoft = Color(0xFF8E8B82),
    accentGraphic = Color(0xFFCC785C),
    accentButton = Color(0xFFA9583E),
    accentDeep = Color(0xFF8F4732),
    accentSoft = Color(0xFFFAF0EA),
    highlight = Color(0xFFF5D9A8),
    success = Color(0xFF5DB872),
    warning = Color(0xFFD4A017),
    error = Color(0xFFC64545),
)

/** 暗色（DESIGN.md「Luzzy 暖幕手记 · 暗色」表，v3 层次重调）。 */
val LuzzyColorsDark = LuzzyColors(
    canvas = Color(0xFF171614),
    surfaceSoft = Color(0xFF201E1B),
    surfaceCard = Color(0xFF2B2824),
    card = Color(0xFF201E1B),
    hairline = Color(0xFF3E3A34),
    hairlineStrong = Color(0xFF6B675F),
    ink = Color(0xFFFAF9F5),
    body = Color(0xFFDED9CF),
    bodyStrongMid = Color(0xFFC4BFB5),
    muted = Color(0xFFA5A198),
    mutedSoft = Color(0xFF8A867D),
    accentGraphic = Color(0xFFD97757),
    accentButton = Color(0xFFB85C3E),
    accentDeep = Color(0xFF9A6244),
    accentSoft = Color(0xFF2E211B),
    highlight = Color(0xFFF5D9A8),
    success = Color(0xFF5DB872),
    warning = Color(0xFFD4A017),
    error = Color(0xFFC64545),
)

/** 动效纪律（DESIGN.md + 硬性规定 9 第 4 步）：进入 200ms / 退出 140ms / ease-out。 */
object LuzzyMotion {
    const val ENTER_MS = 200
    const val EXIT_MS = 140
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
}

/** 圆角（DESIGN.md「Layout & Elevation & Shapes」）。 */
object LuzzyShapes {
    val bubble = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(12.dp)
    val inputIsland = RoundedCornerShape(22.dp)
    val button = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(999.dp)
}

/**
 * 字体栈（硬性规定 4；用户 2026-09-09 选定「全量 1:1 复刻 Web 端」）。
 *
 * 本地 TTF 由 `tools/assistant-fonts.py --all` 从上游 woff2 转换而来（8 枚 / 21.2MB），
 * 落在 `assets/assistant/fonts/`（Compose 只接受 sfnt，不能读 woff2）。**禁止**任何网络字体。
 *
 * **CJK 策略（重要）**：Compose 的 [FontFamily] 按**字重/字形**选字体，**不做逐字形回退**——
 * 与 CSS 的 `font-family: "Lora", "Alibaba PuHuiTi 3.0"` 语义不同。因此：
 * - 正文/UI → 直接以 **Alibaba PuHuiTi 3.0** 为主族（覆盖中英文，观感与 Web 端一致）；
 * - 标题/display → **Lora**（拉丁）+ **PuHuiTi**（中文）用 `Typeface.CustomFallbackBuilder`
 *   串成自定义回退链（API 29+；26-28 退化为 Lora + 系统衬线回退，见 [buildDisplayFamily]）。
 *
 * 体积：8 枚 TTF 合计约 21.2MB（用户已确认接受，APK 约 43MB → 64MB）。
 */
object LuzzyFonts {
    const val ASSET_DIR = "assistant/fonts"
    const val LORA = "Lora"
    const val ALIBABA_SANS = "Alibaba Sans"
    const val PUHUITI = "Alibaba PuHuiTi 3.0"

    /** 从 assets 加载族；文件缺失时回退系统族（不崩溃、不白屏）。 */
    fun load(
        assetManager: AssetManager,
        files: List<Pair<String, Int>>,
        fallback: FontFamily,
    ): FontFamily {
        val available = files.filter { runCatching { assetManager.open(it.first).close() }.isSuccess }
        if (available.isEmpty()) return fallback
        return FontFamily(
            available.map { (path, weight) ->
                Font(path, assetManager, FontWeight(weight))
            }
        )
    }

    /** 单个 asset 字体（用于自定义回退链的构造）。 */
    fun typefaceOf(assetManager: AssetManager, path: String, weight: FontWeight = FontWeight.Normal): Typeface? =
        runCatching { Typeface.createFromAsset(assetManager, path) }.getOrNull()
            ?: null.also { /* 文件缺失 → 交由调用方回退 */ }

    /**
     * display 族：**Lora**（拉丁衬线）；中文由平台回退到系统 CJK 字体。
     *
     * 与 Web 端观感同构：Web 的 `Lora, "Alibaba PuHuiTi 3.0"` 对中文命中的是 PuHuiTi（无衬线），
     * Compose 无法逐字形指定第二字体（`android.graphics.fonts.FontFamily` 需文件路径，
     * 且 API 29+ 才可用），因此中文走系统默认 CJK（同样是无衬线）——**结构一致**，
     * 仅字体实现不同。正文/UI 已用 PuHuiTi，品牌字体覆盖率不受影响。
     */
    fun buildDisplayFamily(assetManager: AssetManager): FontFamily {
        val lora = typefaceOf(assetManager, "$ASSET_DIR/Lora-Regular.ttf") ?: return FontFamily.Serif
        return FontFamily(lora)
    }

}

private fun luzzyTypography(display: FontFamily, body: FontFamily): Typography {
    // 正文 ≥14px、行高 1.55-1.7；caption ≥12px（DESIGN.md Typography）
    val base = Typography()
    return base.copy(
        headlineSmall = TextStyle(
            fontFamily = display, fontSize = 22.sp, lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleLarge = TextStyle(
            fontFamily = display, fontSize = 18.sp, lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = TextStyle(
            fontFamily = body, fontSize = 15.sp, lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
        ),
        bodyLarge = TextStyle(
            fontFamily = body, fontSize = 15.sp, lineHeight = 25.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = body, fontSize = 14.sp, lineHeight = 23.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = body, fontSize = 12.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

private fun luzzyShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = LuzzyShapes.button,
    medium = LuzzyShapes.card,
    large = LuzzyShapes.bubble,
    extraLarge = RoundedCornerShape(24.dp),
)

private fun ColorScheme.withLuzzy(c: LuzzyColors): ColorScheme = copy(
    background = c.canvas,
    onBackground = c.ink,
    surface = c.canvas,
    onSurface = c.ink,
    surfaceVariant = c.surfaceSoft,
    onSurfaceVariant = c.muted,
    surfaceContainerLowest = c.canvas,
    surfaceContainerLow = c.surfaceSoft,
    surfaceContainer = c.surfaceSoft,
    surfaceContainerHigh = c.surfaceCard,
    surfaceContainerHighest = c.surfaceCard,
    outline = c.hairline,
    outlineVariant = c.hairlineStrong,
    primary = c.accentButton,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = c.accentSoft,
    onPrimaryContainer = c.accentDeep,
    secondary = c.accentGraphic,
    onSecondary = Color(0xFFFFFFFF),
    tertiary = c.muted,
    onTertiary = c.canvas,
    error = c.error,
    onError = Color(0xFFFFFFFF),
    errorContainer = c.accentSoft,
    onErrorContainer = c.error,
)

val LocalLuzzyColors = staticCompositionLocalOf { LuzzyColorsLight }

/** 便捷访问：`LuzzyTheme.colors.accentButton`。 */
object LuzzyTheme {
    val colors: LuzzyColors
        @Composable get() = LocalLuzzyColors.current
}

/**
 * 助手页主题入口。所有助手 Compose UI 必须包在本主题内。
 *
 * @param darkTheme 跟随 Web 端主题模式（DataStore `theme_mode`，只读）
 */
@Composable
fun LuzzyAssistantTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) LuzzyColorsDark else LuzzyColorsLight
    val scheme = (if (darkTheme) darkColorScheme() else lightColorScheme()).withLuzzy(colors)
    val assets: AssetManager = LocalContext.current.assets
    val displayFamily = remember(assets) { LuzzyFonts.buildDisplayFamily(assets) }
    val bodyFamily = remember(assets) {
        LuzzyFonts.load(
            assets,
            listOf(
                "${LuzzyFonts.ASSET_DIR}/AlibabaPuHuiTi-3-55-Regular.ttf" to 400,
                "${LuzzyFonts.ASSET_DIR}/AlibabaPuHuiTi-3-65-Medium.ttf" to 500,
                "${LuzzyFonts.ASSET_DIR}/AlibabaPuHuiTi-3-85-Bold.ttf" to 700,
            ),
            FontFamily.SansSerif,
        )
    }
    // Typography/Shapes 必须 remember：否则每次主题重组都新建对象，
    // MaterialTheme 的 CompositionLocal 值变化会让整棵子树失效（实测卡顿来源之一）。
    val typography = remember(displayFamily, bodyFamily) { luzzyTypography(displayFamily, bodyFamily) }
    val shapes = remember { luzzyShapes() }
    CompositionLocalProvider(LocalLuzzyColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}
