package com.luzzymeow.luzzyrp.ui.markdown

import android.graphics.Color
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView

/**
 * **HTML 卡片**：把模型写在正文里的 HTML 渲染成它本来的样子（RP 卡片 / 铭文面板 / 状态条）。
 *
 * ## 为什么用 WebView
 *
 * 卡片的样式是**模型自己写的内联 CSS**（`style="background:#0d1416;border:1px solid …"`）。
 * 要在 Compose 里复刻它就得自造一个 HTML/CSS 引擎——那永远追不上模型的写法。
 * 上游本来就是浏览器（`v-html`），所以这里用同一个渲染内核（系统 WebView）来保证保真：
 * **内联样式、`<style>` 块、表格、SVG 都能原样显示**。
 *
 * ## 安全边界（三条，缺一不可）
 *
 * 1. **容器层**（本文件）：JS 关闭、DOM Storage 关闭、文件/内容访问关闭、**网络加载关闭**；
 * 2. **内容层**（[HtmlSanitizer]）：剥掉 `script` / `iframe` / `on*` / 脚本 URL / 死控件；
 * 3. **文档层**：注入的 CSP（`default-src 'none'; script-src 'none'` …）。
 *
 * 与上游的**有意偏离**：上游把消息 HTML 和整页应用放在同一个文档里，并靠 `DOMPurify` 的
 * 白名单放行 `script`（那是它们的「可执行卡片」特性）。我们不给执行能力——代价是
 * 依赖 JS 的卡片只会显示成静态帧；好处是**模型输出无法碰到应用**。
 *
 * ## 与宿主环境对齐（设计门评审要求，2026-09-14）
 *
 * - **字号跟随系统**：`textZoom` 由 `LocalDensity.fontScale` 算出，否则正文会缩放而卡片不会；
 * - **主题色兜底**：把 Compose 主题的正文色/链接色注进文档（低特异性），
 *   只写深底没写 `color` 的卡片在亮主题下才可读；
 * - **减少动效**：系统动画时长为 0 时连同卡内 `animation/transition` 一起停用。
 *
 * 天花板（`ponytail:` 有意简化）：网络被禁 → 卡片里引用网络图片/字体不会加载
 * （`data:` 内联图可以）。要放开就得接受「模型能让设备去访问任意地址」，
 * 那是产品决策而不是实现细节，等用户明确要再做。
 */
@Composable
fun HtmlCard(html: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb().toCssHex()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb().toCssHex()
    val baseFontSize = MaterialTheme.typography.bodyLarge.fontSize.value.let {
        if (it > 0f) it.toInt() else 15
    }
    // 系统动画关闭（开发者选项/无障碍里的「移除动画」）→ 卡内动效也停
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val document = remember(html, textColor, linkColor, baseFontSize, reduceMotion, fontScale) {
        HtmlSanitizer.document(
            html = html,
            textColorCss = textColor,
            linkColorCss = linkColor,
            baseFontSizePx = baseFontSize,
            reduceMotion = reduceMotion,
        )
    }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                // 卡片自带背景；没写背景时透出气泡玻璃（与上游「透出页面背景」同义）
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    // 跟随系统字体缩放（正文是按 sp 缩放的，卡片不跟就会不一致）
                    textZoom = (fontScale * 100).toInt().coerceIn(50, 300)
                }
                // 载入完成后重量一次：WebView 在 wrap_content 下的高度要等内容量出来
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.requestLayout()
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != document) {
                view.tag = document
                view.settings.textZoom = (fontScale * 100).toInt().coerceIn(50, 300)
                view.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        },
    )
}

/** `#AARRGGBB` → `#RRGGBB`（CSS 不认 Compose 的 alpha 前缀写法）。 */
private fun Int.toCssHex(): String = String.format("#%06X", this and 0xFFFFFF)
