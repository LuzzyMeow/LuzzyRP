package com.luzzymeow.luzzyrp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.ui.icons.GameIcon
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.AuroraColor
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme

/**
 * 应用入口 Activity（P0 引导壳）。
 *
 * P6/P7 里程碑将替换为 Navigation3 路由壳（RouteActivity + NavDisplay）。
 * 当前职责：验证 Aurora Dual 主题 / 混排字体 / 图标资产管线在真机上的呈现。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LuzzyTheme {
                BootstrapScreen()
            }
        }
    }
}

/** P0 引导屏：品牌标识 + 设计令牌（品牌色/扩展色）+ 语义图标抽样网格。 */
@Composable
private fun BootstrapScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LuzzySpacing.LG),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(LuzzySpacing.XXXL))

            // 品牌标识（mipmap 启动图标同源）
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = "LuzzyRP",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(LuzzySpacing.LG))
            Text(
                text = "LuzzyRP",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(LuzzySpacing.SM))
            Text(
                text = "每次对话，都像一本有你的小说。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(LuzzySpacing.XL))
            BrandSwatches()
            Spacer(Modifier.height(LuzzySpacing.XL))

            // 语义图标抽样（来自 LuzzyIcons 注册表，验证管线产物）
            val sample = listOf(
                LuzzyIcons.NewChat, LuzzyIcons.History, LuzzyIcons.Star,
                LuzzyIcons.Settings, LuzzyIcons.Send, LuzzyIcons.Edit,
                LuzzyIcons.Memory, LuzzyIcons.Book, LuzzyIcons.Map,
                LuzzyIcons.Dice, LuzzyIcons.Sword, LuzzyIcons.Shield,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LuzzySpacing.MD),
                verticalArrangement = Arrangement.spacedBy(LuzzySpacing.MD),
            ) {
                items(sample) { icon -> SampleIconCell(icon) }
            }
        }
    }
}

@Composable
private fun BrandSwatches() {
    Row(horizontalArrangement = Arrangement.spacedBy(LuzzySpacing.SM)) {
        listOf(
            "Pink" to AuroraColor.AuroraPink,
            "Violet" to AuroraColor.AuroraViolet,
            "Paper" to AuroraColor.CanvasLight,
            "Night" to AuroraColor.CanvasDark,
        ).forEach { (label, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.height(LuzzySpacing.XS))
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SampleIconCell(icon: GameIcon) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(icon.res),
                    contentDescription = icon.name,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
