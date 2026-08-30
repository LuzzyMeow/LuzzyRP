package com.luzzymeow.luzzyrp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 正则脚本领域模型：五作用域 × 四时机 × 深度区间。
 * 参考 SillyTavern 正则与旧项目 Task-V0.3.0 的编辑器规格。
 */

/** 作用范围。 */
@Serializable
enum class RegexScope {
    /** 用户消息。 */
    @SerialName("user") USER,

    /** AI 正文。 */
    @SerialName("ai") AI,

    /** AI 推理（思考卡片文本）。 */
    @SerialName("reasoning") REASONING,

    /** 世界书条目内容。 */
    @SerialName("worldbook") WORLDBOOK,
}

/** 生效时机。 */
@Serializable
enum class RegexTiming {
    /** 仅显示（本地渲染前替换，不改发送内容）。 */
    @SerialName("display") DISPLAY,

    /** 仅发送（组装请求前替换，不改本地显示）。 */
    @SerialName("send") SEND,

    /** AI 接收（工具输出/世界书等注入内容的接收处理）。 */
    @SerialName("ai_receive") AI_RECEIVE,

    /** 用户接收（导入外部内容的处理）。 */
    @SerialName("user_receive") USER_RECEIVE,
}

@Serializable
data class RegexScript(
    val id: String,
    val name: String,
    /** 查找正则（RE2 兼容子集 + Java 正则）。 */
    val find: String,
    /** 替换模板：支持 {{match}}（整体匹配）与 $1..$n（分组）。 */
    val replace: String = "{{match}}",
    val scopes: List<RegexScope> = listOf(RegexScope.AI),
    val timings: List<RegexTiming> = listOf(RegexTiming.DISPLAY),
    /** 生效深度区间（距末尾消息数；null = 不限）。 */
    val minDepth: Int? = null,
    val maxDepth: Int? = null,
    val enabled: Boolean = true,
    /** 绑定角色卡（null = 全局）。 */
    val cardId: String? = null,
    /** 是否来自内置预设（内置预设可停用不可删除）。 */
    val preset: String? = null,
)

/** 内置正则预设（导入卡/新建时可直接套用）。 */
object RegexPresets {

    /** 思维链剥离：<think>…</think> 整块从正文中移除（思考卡片另行捕获）。 */
    val THINK_STRIP = RegexScript(
        id = "preset_think_strip",
        name = "思维链剥离",
        find = "<think>[\\s\\S]*?</think>",
        replace = "",
        scopes = listOf(RegexScope.AI),
        timings = listOf(RegexTiming.DISPLAY),
        preset = "think_strip",
    )

    /** 引用高亮："…" 转为内置引用标记。 */
    val QUOTE_HIGHLIGHT = RegexScript(
        id = "preset_quote",
        name = "引用高亮",
        find = "\"([^\"]{1,200}?)\"",
        replace = "「$1」",
        scopes = listOf(RegexScope.AI),
        timings = listOf(RegexTiming.DISPLAY),
        preset = "quote",
    )

    /** 旁白标记：（…）转斜体标记。 */
    val ASIDE_ITALIC = RegexScript(
        id = "preset_aside",
        name = "旁白斜体",
        find = "\\(([^()]{1,200}?)\\)",
        replace = "*($1)*",
        scopes = listOf(RegexScope.AI),
        timings = listOf(RegexTiming.DISPLAY),
        preset = "aside",
    )

    /** Markdown 代码块保留（防止误替换）。 */
    val CODE_BLOCK_GUARD = RegexScript(
        id = "preset_code_guard",
        name = "代码块保护",
        find = "```[\\s\\S]*?```",
        replace = "{{match}}",
        scopes = listOf(RegexScope.AI),
        timings = listOf(RegexTiming.DISPLAY, RegexTiming.SEND),
        preset = "code_guard",
    )

    /** 标签清理：剥离 XML 风格标签。 */
    val TAG_CLEANUP = RegexScript(
        id = "preset_tag_cleanup",
        name = "标签清理",
        find = "</?[a-zA-Z][a-zA-Z0-9_-]*>",
        replace = "",
        scopes = listOf(RegexScope.AI),
        timings = listOf(RegexTiming.DISPLAY),
        preset = "tag_cleanup",
    )

    val ALL = listOf(THINK_STRIP, QUOTE_HIGHLIGHT, ASIDE_ITALIC, CODE_BLOCK_GUARD, TAG_CLEANUP)
}
