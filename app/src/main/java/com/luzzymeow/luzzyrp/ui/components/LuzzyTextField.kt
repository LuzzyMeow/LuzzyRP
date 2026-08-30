package com.luzzymeow.luzzyrp.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing

/**
 * 统一文本输入框（设置/编辑/搜索共用）：统一圆角与描边色，图标可选。
 */
@Composable
fun LuzzyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIconRes: Int? = null,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) minLines * 4 else 1,
    singleLine: Boolean = maxLines == 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        leadingIcon = leadingIconRes?.let {
            { Icon(painterResource(it), contentDescription = null) }
        },
        minLines = minLines,
        maxLines = maxLines,
        singleLine = singleLine,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(LuzzySpacing.MD),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        ),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}
