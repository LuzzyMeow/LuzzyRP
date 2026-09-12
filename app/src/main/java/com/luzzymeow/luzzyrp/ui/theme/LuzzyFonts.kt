package com.luzzymeow.luzzyrp.ui.theme

import android.os.Build
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.R

/**
 * v3.0 字体栈（硬性规定 4；设计真源 `docs/DESIGN-compose.md` §3）。
 *
 * 8 枚本地 TTF 落 `res/font/`（自 git 历史 52aab12c 恢复，与 Web 端 woff2 1:1 同源，
 * 由当年的 woff2→TTF 转换管线产出）。**禁止运行时 CDN**。
 *
 * **CJK 策略（承袭 52aab12c 实证结论）**：Compose 的 [FontFamily] 按字重选字体、
 * 不做逐字形回退——
 * - 正文/UI → 直接以 **Alibaba PuHuiTi 3.0** 为主族（覆盖中英文，观感与 Web 端一致）；
 * - display（角色名牌/区块标题）→ **Lora**；中文经平台回退到系统 CJK（同为无衬线，结构一致）。
 */
object LuzzyFonts {

    /** display：Lora Regular/Italic（拉丁衬线，品牌文学声音）。 */
    val Lora: FontFamily = FontFamily(
        Font(R.font.lora_regular, FontWeight.Normal),
        Font(R.font.lora_italic, FontWeight.Normal, style = androidx.compose.ui.text.font.FontStyle.Italic),
    )

    /** 正文/UI：PuHuiTi 三字重（中文主族，拉丁与数字也由它覆盖）。 */
    val Body: FontFamily = FontFamily(
        Font(R.font.puhuiti_55_regular, FontWeight.Normal),
        Font(R.font.puhuiti_65_medium, FontWeight.Medium),
        Font(R.font.puhuiti_85_bold, FontWeight.Bold),
    )

    /** 拉丁无衬线（备用于纯拉丁数字场合，如用量数字）。 */
    val AlibabaSans: FontFamily = FontFamily(
        Font(R.font.alibaba_sans_regular, FontWeight.Normal),
        Font(R.font.alibaba_sans_medium, FontWeight.Medium),
        Font(R.font.alibaba_sans_bold, FontWeight.Bold),
    )

    /** 等宽（模型 ID / 代码块）：系统族，不单独打包（承袭既有决策）。 */
    val Mono: FontFamily = FontFamily.Monospace

    /**
     * display 的中文回退链：Lora（拉丁）→ PuHuiTi（中文），API 29+ 用
     * [Typeface.CustomFallbackBuilder] 串自定义回退；26-28 退化为纯 Lora
     * （中文由平台回退系统 CJK，结构一致）。
     *
     * res/font 资源经 `androidx.core.content.res.ResourcesCompat` 取 Typeface；
     * 失败（构造受限等）一律回退纯 [Lora]。
     */
    fun buildDisplayFamily(): FontFamily {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Lora
        val ctx = appContext ?: return Lora
        return try {
            val res = ctx.resources
            val family = android.graphics.fonts.FontFamily.Builder(
                android.graphics.fonts.Font.Builder(res, R.font.lora_regular).build()
            ).addFont(android.graphics.fonts.Font.Builder(res, R.font.puhuiti_55_regular).build()).build()
            val custom = Typeface.CustomFallbackBuilder(family)
                .setSystemFallback("sans-serif")
                .build()
            FontFamily(custom)
        } catch (t: Throwable) {
            Lora
        }
    }

    /** 由宿主注入（ComposeActivity.onCreate），供回退链构建使用。 */
    @Volatile
    var appContext: android.content.Context? = null
}

/** M3 Typography：默认骨架 + Luzzy 字体覆盖（DESIGN-compose §3：正文 ≥14sp/1.68 行高）。 */
@Suppress("unused")
private val base = Typography()

val LuzzyTypography = Typography().copy(
    headlineSmall = TextStyle(
        fontFamily = LuzzyFonts.Lora, fontSize = 22.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontFamily = LuzzyFonts.Lora, fontSize = 18.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = LuzzyFonts.Body, fontSize = 15.sp, lineHeight = 23.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = LuzzyFonts.Body, fontSize = 15.sp, lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = LuzzyFonts.Body, fontSize = 14.sp, lineHeight = 23.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = LuzzyFonts.Body, fontSize = 12.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
)