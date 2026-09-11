package com.luzzymeow.luzzyrp.chat

/**
 * 原生 → JS 调用串（经 `WebView.evaluateJavascript`）。
 *
 * 事件回调固定为 `window.Luzzy.chatNative.onEvent(jobId, eventJsonString)`。
 * 整串包了存在性检测：JS 胶水层（`assets/ext/luzzy-chat-native.js`）尚未加载、
 * 或上游页面结构变化时**静默丢弃**而不是往控制台刷异常。
 */
object ChatJsCall {

    /**
     * 构造 `onEvent` 调用表达式。
     *
     * [jobId] 与 [eventJson] 都按 JS 字符串字面量转义——[eventJson] 里必然含引号
     * 与换行（模型正文），直接拼串会语法错误。
     */
    fun onEvent(jobId: String, eventJson: String): String = buildString {
        append("try{var b=window.Luzzy&&window.Luzzy.chatNative;")
        append("if(b&&typeof b.onEvent==='function'){b.onEvent(")
        append(jsStringLiteral(jobId))
        append(',')
        append(jsStringLiteral(eventJson))
        append(");}}catch(e){}")
    }

    /**
     * 单引号 JS 字符串字面量。
     *
     * 除常规转义外，额外转义 `U+2028` / `U+2029`——它们在旧 WebView 的 JS 词法里
     * 算行终止符，会把一个字符串字面量劈成两行（模型正文里真的出现过）。
     */
    fun jsStringLiteral(value: String): String = buildString(value.length + 2) {
        append('\'')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> append(ch)
            }
        }
        append('\'')
    }
}
