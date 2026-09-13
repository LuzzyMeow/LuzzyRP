package com.luzzymeow.luzzyrp.chat

/**
 * 正文占位符替换（上游 `replaceUserNamePlaceholder` 的对应物）。
 *
 * ## 上游怎么做的（照抄它的语义，不发明新的）
 *
 * `app.js` 里只有一处替换：`{{\s*user\s*}}` → `user.name`（大小写不敏感），
 * 而 **`{{char}}` 上游并不替换**——真实数据里模型的输出会带 `{{char}}`，
 * 旧版 WebView 也是**原样显示**的（这一点已在源码里核实）。
 *
 * ## 那我们为什么还要替换 `{{char}}`
 *
 * 因为「与上游一致」在这里并不等于「对」：满屏 `{{char}}把手抬起来…` 是明显的坏体验，
 * 而且 DESIGN-compose §20.3 把这个缺口登记为待修。规则里的 `{{char}}` 是**卡片语法的占位符**
 * （角色卡/预设里用它指代角色名），渲染期替换成真实名字是它本来该有的样子。
 *
 * ## 纪律
 *
 * **只在渲染期替换，绝不改写存储**——`ChatMessage.raw` 保持与旧版逐字一致
 * （迁移保真、发给模型的上下文也不变）。这也正是上游的做法（替换发生在 `processRegex` 之前）。
 */
object Placeholders {

    private val CHAR = Regex("""\{\{\s*char\s*\}\}""", RegexOption.IGNORE_CASE)
    private val USER = Regex("""\{\{\s*user\s*\}\}""", RegexOption.IGNORE_CASE)

    /**
     * @param charName 当前角色名；为空则**保留原占位符**（宁可不替换，也不要替换成空白）
     * @param userName 当前用户名；同样为空则保留
     */
    fun render(text: String, charName: String, userName: String): String {
        if (text.isEmpty()) return text
        if (!text.contains("{{")) return text
        var out = text
        if (charName.isNotBlank()) out = CHAR.replace(out, charName)
        if (userName.isNotBlank()) out = USER.replace(out, userName)
        return out
    }
}
