package com.luzzymeow.luzzyrp.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * **减弱动效**（C7）——系统的「移除动画」（开发者选项 / 无障碍）是否开启。
 *
 * ## 为什么必须有这一层
 *
 * 自绘动效（无限循环的光斑漂移、点呼吸、打字三点）**不读系统的动画缩放**：
 * `rememberInfiniteTransition` 与手写时间线都跑在自己的时钟上，于是「开了移除动画」的用户
 * 在这台设备上仍然会看到满屏流动 —— 对前庭敏感的人群是实打实的不适来源。
 * `prefers-reduced-motion` 的 Compose 等价物就是它。
 *
 * ## 判定口径
 *
 * `ANIMATOR_DURATION_SCALE == 0f` 才算「移除动画」（与 `ui/markdown/HtmlCard` 一直在用的
 * 口径相同、也与 Android 官方建议一致）。**不把 0.5 之类折算进来**：缩放是「快慢」，
 * 是否移除动效是「有没有」，两者语义不同；把 0.5 当「移除」会违背用户本意。
 *
 * ## 为什么订阅变化而不是只读一次
 *
 * 用户从开发者选项改了这项再切回应用，页面不该还按旧值跑（那正是「设置了没用」的观感）。
 * 用 `ContentObserver` 订阅 + `produceState` 广播，值变了所有消费点一起重组。
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val state by produceState(initialValue = readReduceMotion(context), context) {
        // 初始值同步读（首帧就要正确：否则会先播一轮动画再停，观感突兀）
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = readReduceMotion(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        // produceState 的 awaitDispose 等价物：协程被取消时注销
        try {
            while (true) delay(Long.MAX_VALUE)
        } finally {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return state
}

/** 同步读一次（非 Compose 上下文也能用）。读失败按「不减弱」处理（动画照常，不静默关掉）。 */
fun readReduceMotion(context: Context): Boolean = runCatching {
    reduceMotionFromScale(
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
    )
}.getOrDefault(false)

/**
 * 「动画缩放值 → 是否减弱动效」的**纯算式**（无 Context，可直接 JVM 单测）。
 *
 * 三条口径都钉在测试里：`0f` 才算；`0.5f` 这类「快一点」不算；`NaN`（读坏了）不算
 * —— 读坏的默认方向是「动画照常播」，而不是「静默把全应用动效关掉」
 * （后者会让一个读取失败变成用户可见的功能变化）。
 */
internal fun reduceMotionFromScale(scale: Float): Boolean = !scale.isNaN() && scale == 0f

/**
 * 一次自绘动效的**实际时长**：减弱动效时归零（C7）。
 *
 * 纯函数（可 JVM 单测）：`scaledDuration(200, reduce = true) == 0`。
 *
 * 为什么是「归零」而不是「只缩短」：无限循环类动效（背景光斑、点呼吸）缩短后依然在动、
 * 依然会引发不适；归零让它们停在首帧 —— 那正是「移除动画」的语义。
 * 一次性过渡同样归零：动画结束的状态就是最终状态，跳过它不丢信息。
 *
 * 在 Composable 里配 [rememberMotionDurations] 用（一次读系统设置 + 折算好两个时长）。
 */
fun scaledDuration(baseMs: Int, reduce: Boolean): Int = if (reduce) 0 else baseMs.coerceAtLeast(0)

/**
 * 进入 / 退出两个时长（已按「减弱动效」折算）。
 *
 * 存在的理由：一次过渡要用两个时长（`enter = scaledDuration(EnterMs)`、
 * `exit = scaledDuration(ExitMs)`），逐处写就是四行 + 两遍读系统设置；
 * 这里一次给出，调用点只写 `tween(durations.enterMs)`。
 *
 * [reduce] 也一并带出来：需要「跳过动效但保留终态取值」的场合（点呼吸、光斑漂移）
 * 用它做分支，而不是把时长归零（时长归零对无限循环动效没有意义）。
 */
data class MotionDurations(val enterMs: Int, val exitMs: Int, val reduce: Boolean)

@Composable
fun rememberMotionDurations(enterMs: Int = 200, exitMs: Int = 140): MotionDurations {
    val reduce = rememberReduceMotion()
    return MotionDurations(
        enterMs = scaledDuration(enterMs, reduce),
        exitMs = scaledDuration(exitMs, reduce),
        reduce = reduce,
    )
}

/**
 * 自绘循环动效的**相位**（0..1）：减弱动效时恒为 [reduceValue]（默认 1f = 一轮走完）。
 *
 * 只给自绘 Canvas 用（`rememberInfiniteTransition` 覆盖不到的场合）。
 * 一次性过渡不需要它 —— 直接用 [scaledDuration] 把时长归零即可。
 *
 * 时钟用 [SystemClock.uptimeMillis]：`System.currentTimeMillis()` 会被用户改时间或 NTP
 * 校时**向后跳**，那会让动画瞬间倒退（观感是「画面闪一下」）。
 */
@Composable
fun rememberLoopPhase(periodMs: Int, reduce: Boolean, reduceValue: Float = 1f): Float {
    val phase by produceState(
        initialValue = if (reduce) reduceValue else 0f,
        periodMs, reduce, reduceValue,
    ) {
        if (reduce) {
            value = reduceValue
            return@produceState
        }
        val start = SystemClock.uptimeMillis()
        while (true) {
            if (periodMs <= 0) {
                value = reduceValue
                return@produceState
            }
            value = ((SystemClock.uptimeMillis() - start).toFloat() / periodMs).coerceIn(0f, 1f)
            delay(16L)
        }
    }
    return phase
}
