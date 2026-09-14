package com.luzzymeow.luzzyrp.testing

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import kotlinx.coroutines.runBlocking

/**
 * 仪器化测试的**等待纪律**——所有「等界面/等数据库」都走这里，别各写一套。
 *
 * ## 为什么不能用 `compose.waitUntil { … }`（会话 78 系统性踩中，5 个测试文件都红了）
 *
 * `waitUntil` 的实现是「反复求值条件直到成立」——它**只在测试时钟的当前帧上打转**，
 * 不推进时间。而本应用里两类东西都需要**时间前进**才会推进：
 *
 * 1. `LaunchedEffect` 里的**取数协程**（面板内容、页面数字）——续体恢复挂在帧回调上；
 * 2. `rememberCoroutineScope()` 里的**落盘协程**（写库）——同上。
 *
 * 于是现象是「面板永远停在『读取中…』」「界面上消息出来了但库里 8 秒都没写进去」，
 * 超时消息只是表象。**这是测试写法问题，不是产品缺陷**（会话 78 用四组对照探针证实：
 * 同一份代码推进时钟后 200ms 内即绿）。
 *
 * ## 为什么是三步（缺一不可）
 *
 * | 步骤 | 作用 |
 * |---|---|
 * | [ComposeTestRule.mainClock]`.advanceTimeBy` | 推进**测试时钟**：协程续体靠它恢复 |
 * | [ComposeTestRule.waitForIdle] | 让推进后的帧真正跑完（重组 / 布局 / 绘制） |
 * | `Thread.sleep(50)`（**只在条件未成立时**） | 让出**真实时间**：库查询在后台线程上，测试时钟推不动它 |
 *
 * 特别注意第三步：这也是为什么不能改成「纯 `Thread.sleep` 轮询」——那样没有帧，
 * 协程不会执行（`docs/CHAT-REGRESSION.md` §4.1 记录过这个反向的坑）。
 *
 * ## 超时行为
 *
 * 超时后再推一次时钟并抛**带描述**的 [AssertionError]（比裸超时好定位）。
 * 若要看现场，把 `dumpTree = true` 打开——会逐 root 把语义树写进 logcat
 * （多 root 时 `onRoot()` 会抛异常，所以必须逐 root 且在 `runCatching` 里）。
 */
object Await {
    const val STEP_BUDGET_MS = 100L
    const val REAL_TIME_YIELD_MS = 50L

    fun until(
        rule: ComposeTestRule,
        what: String,
        timeoutMs: Long = 8_000,
        dumpTree: Boolean = false,
        condition: () -> Boolean,
    ) {
        // 先让出**真实时间**再开始轮询：BottomSheet / Dialog 的**进入动画**由真实帧驱动，
        // 而内容组合（`LaunchedEffect` 里的取数）要等进入动画让出主线程后才被调度。
        // 只推测试时钟不产出真实帧 → 面板会永远停在「读取中…」（会话 78 整套跑实测的失败形态）。
        Thread.sleep(REAL_TIME_YIELD_MS * 2)
        rule.waitForIdle()

        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (condition()) return
            if (System.currentTimeMillis() >= deadline) break
            // 双管齐下：推进测试时钟（帧驱动的组合/协程）**并**让出真实时间（后台查询与动画）
            rule.mainClock.advanceTimeBy(STEP_BUDGET_MS)
            rule.waitForIdle()
            Thread.sleep(REAL_TIME_YIELD_MS)
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        if (condition()) return
        if (dumpTree) dumpAllRoots(rule, what)
        throw AssertionError("等待超时（${timeoutMs}ms）：$what 未出现")
    }

    /**
     * 等界面上的某段文字。
     *
     * **默认精确匹配**：`substring = true` 会让「分支首句」也算匹配「首句」，
     * 于是「某条消息可见」这类断言会假绿（`CHAT-REGRESSION.md` §4.3 记录过）。
     * 只有「文案本来就带数字/变体」的场合（如「还没有可统计的用量记录」的提示行）
     * 才传 `substring = true`，并且调用方要清楚自己在放宽什么。
     */
    fun text(
        rule: ComposeTestRule,
        text: String,
        timeoutMs: Long = 8_000,
        substring: Boolean = false,
    ) = until(
        rule = rule,
        what = "text=$text" + if (substring) "（子串匹配）" else "",
        timeoutMs = timeoutMs,
        condition = {
            rule.onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring))
                .fetchSemanticsNodes().isNotEmpty()
        },
    )

    /** 等数据库条件成立（条件里只查库，**不要调 Compose API**）。 */
    fun db(rule: ComposeTestRule, timeoutMs: Long = 8_000, condition: suspend () -> Boolean) = until(
        rule = rule,
        what = "数据库条件",
        timeoutMs = timeoutMs,
        condition = { runBlocking { condition() } },
    )

    /**
     * 把当前**每一个 root** 的语义树写入 logcat（tag `LuzzyTest`）。
     *
     * 为什么逐 root：`BottomSheet` / `Dialog` 是独立窗口（第二个 root），
     * `rule.onRoot()` 在多 root 时会抛 `Expected exactly '1' node but found N`——
     * 诊断代码自己变成新的失败源（会话 78 踩过一次）。所以这里逐个取、且整段 `runCatching`。
     */
    fun dumpAllRoots(rule: ComposeTestRule, reason: String) {
        runCatching {
            val roots = rule.onAllNodes(isRoot()).fetchSemanticsNodes().size
            android.util.Log.e("LuzzyTest", "等待超时：$reason（当前 root 数=$roots）")
            repeat(roots) { i ->
                val dumped = runCatching {
                    rule.onAllNodes(isRoot())[i].printToString(maxDepth = 10)
                }.getOrElse { "<dump 失败：$it>" }
                android.util.Log.e("LuzzyTest", "ROOT[$i]:\n$dumped")
            }
        }
    }
}
