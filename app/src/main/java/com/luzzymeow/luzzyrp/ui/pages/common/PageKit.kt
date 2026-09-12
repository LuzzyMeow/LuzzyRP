package com.luzzymeow.luzzyrp.ui.pages.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts

/**
 * 页面通用组件（DESIGN-compose §13.3；命名对齐上游 settings-page-header 语义）。
 */

/** 页头（M3 TopAppBar 透明底）：汉堡（开抽屉）+ 页图标 + 标题 + 右侧动作槽。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageHeader(
    title: String,
    iconRes: Int,
    onOpenDrawer: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = title,
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    painter = painterResource(LuzzyIcons.Menu),
                    contentDescription = "打开菜单",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** 分组标题（上游 settings-section-heading：12sp uppercase 字距）。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontFamily = LuzzyFonts.Body,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** 设置卡容器（card 底 + hairline 边 + 16dp 圆角）。 */
@Composable
fun SettingCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

/** 卡内行：leading 图标 + 标题 + 支撑文本 + trailing 槽。 */
@Composable
fun SettingRow(
    title: String,
    supporting: String? = null,
    leadingIconRes: Int? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingIconRes != null) {
            Icon(
                painter = painterResource(leadingIconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    fontSize = 12.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/** 胶囊徽标（范围/注入位置/状态等语义色块）。 */
@Composable
fun BadgeChip(text: String, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(tint.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

/** 开关（静态占位，承袭上游 settings-toggle 44×24 语义）。 */
@Composable
fun LuzzySwitch(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .background(
                if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(12.dp),
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(20.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/** 空态（居中图标 + 主副文；上游空态语义）。 */
@Composable
fun EmptyState(iconRes: Int, title: String, supporting: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = title,
            fontSize = 14.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = supporting,
            fontSize = 12.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}