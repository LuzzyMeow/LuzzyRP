package com.luzzymeow.luzzyrp.ui.pages.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.theme.Loom
import com.luzzymeow.luzzyrp.ui.theme.LoomShape
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts

/**
 * **带纹理头带的设置卡**（v3.1 Loom 化：hero 圆角 24 + 顶缘高光 + 针脚缝）。
 *
 * 与 [SettingCard] 的关系：同一张卡壳（hairline 边 + 织层底），但顶部有 **120dp 渐变织带**——
 * 白字标题与图标**内嵌带内**，带面叠一层白色波形织纹（两层错位），底缘三枚「针脚」收尾。
 *
 * ## 色相纪律（保留）
 *
 * 头带颜色**只从 M3 role 取**（调用方传 `colorScheme.primary` / `secondary` / `tertiary`…），
 * 第二色由 [bandTone] 从同一色相与画布混出 —— **不再有硬编码色值**，
 * 亮暗两套自动跟随。带上的文字色同理必须传 **on 色**（`onPrimary` …）：
 * 暗色下 `primary` 是浅桃色，白字压上去会读不清（历史真机截图抓到的对比度缺陷）。
 *
 * @param trailing 头带右侧的动作槽（白字/白玻璃按钮应在调用方自行着色）
 * @param avatar 头像等**叠压件**：压在头带下沿（半出带外），内容区自动让出 30dp
 */
@Composable
fun BandCard(
    title: String,
    iconRes: Int,
    bandFirst: Color,
    bandSecond: Color,
    /** 头带上的文字/图标色：**必须传对应 role 的 on 色**（`onPrimary` / `onSecondary` / `onTertiary`）。 */
    bandContent: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    avatar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val loom = Loom.current
    val shape = RoundedCornerShape(LoomShape.Hero)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = loom.card,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, loom.hairline),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRect(Brush.horizontalGradient(listOf(bandFirst, bandSecond)))
                    // 顶缘微光（织层高光，与 LoomCard 同一语言）
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                            startY = 0f,
                            endY = h * 0.22f,
                        ),
                    )
                    // 织纹：两层错位波形（上游是一层波纹；两层在同色带面上更像织物）
                    val upper = Path().apply {
                        moveTo(0f, h * 0.72f)
                        cubicTo(w * 0.26f, h * 0.40f, w * 0.54f, h * 0.98f, w * 0.78f, h * 0.56f)
                        cubicTo(w * 0.88f, h * 0.38f, w, h * 0.52f, w, h * 0.52f)
                        lineTo(w, h); lineTo(0f, h); close()
                    }
                    drawPath(upper, Color.White.copy(alpha = 0.16f))
                    val lower = Path().apply {
                        moveTo(0f, h * 0.88f)
                        cubicTo(w * 0.22f, h * 0.66f, w * 0.48f, h * 1.04f, w * 0.72f, h * 0.78f)
                        cubicTo(w * 0.86f, h * 0.62f, w, h * 0.74f, w, h * 0.74f)
                        lineTo(w, h); lineTo(0f, h); close()
                    }
                    drawPath(lower, Color.White.copy(alpha = 0.08f))
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 玻璃圆图标（Loom hero 语义）
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = bandContent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = title,
                        color = bandContent,
                        fontFamily = LuzzyFonts.Body,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                }
                // 针脚缝（三枚点，织机收尾语义）
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(3.dp)
                                .background(Color.White.copy(alpha = 0.5f), CircleShape),
                        )
                    }
                }
                avatar?.let {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp)
                            .offset(y = 22.dp),
                    ) { it() }
                }
            }
            Column(
                Modifier.padding(
                    // 左右不额外缩进：`SettingRow` 自带 16dp，与头带标题同一条竖线
                    start = 0.dp,
                    end = 0.dp,
                    top = if (avatar != null) 30.dp else 6.dp,
                    bottom = 8.dp,
                ),
                content = content,
            )
        }
    }
}

/**
 * 头带第二色：**同色相的 T 阶**——与画布按 34% 混合。
 *
 * 为什么不用 `color.copy(alpha)`：alpha 叠加在背景上会透出画布，暗色下滑向灰；
 * 用 [lerp] 得到的是**不透明的同色相深浅**，亮暗两套都稳。
 * 34% 是实测取值：18% 时两色差过小，头带看着像纯色块、纹理也显不出来。
 */
fun bandTone(color: Color, surface: Color): Color = lerp(color, surface, 0.34f)