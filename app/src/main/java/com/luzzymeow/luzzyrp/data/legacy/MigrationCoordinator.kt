package com.luzzymeow.luzzyrp.data.legacy

import android.content.Context
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 迁移协调器：进程内**只跑一次**，并让界面能知道「现在能不能读数据」。
 *
 * 为什么需要它（而不是直接调 [MigrationRunner]）：界面启动时并不知道迁移有没有跑完。
 * 如果不管，会出现「迁移还在读旧库，聊天页已经按空库渲染成演示数据了」——
 * 用户看到的是演示角色，随后数据悄悄出现（或者要重启才出现）。
 * 所以界面**等这个状态离开 [State.Running] 再读**。
 *
 * 状态机很浅，但把三种结局分清楚了：
 * - [State.Skipped]：没有旧数据 / 早就迁过 → 直接读新库（正常路径）；
 * - [State.Done]：迁完了 → 读新库 + 提示一行报告；
 * - [State.Failed]：导出或写入失败 → **不写完成标记**，下次启动重来；界面照常进空库。
 */
object MigrationCoordinator {

    sealed interface State {
        data object Idle : State
        data object Running : State
        data object Skipped : State
        data class Done(val report: String) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val mutex = Mutex()

    /** 幂等：重复调用只跑一次（Activity 重建、多次进入都会调）。 */
    suspend fun ensureMigrated(context: Context) {
        mutex.withLock {
            if (_state.value != State.Idle) return
            _state.value = State.Running
            val store = LuzzyStore(DatabaseProvider.luzzy(context))
            _state.value = try {
                when (val outcome = MigrationRunner(context, store).runIfNeeded()) {
                    is MigrationRunner.Outcome.Skipped -> State.Skipped
                    is MigrationRunner.Outcome.Migrated -> State.Done(outcome.report)
                    is MigrationRunner.Outcome.Failed -> State.Failed(outcome.reason)
                }
            } catch (e: Throwable) {
                // 迁移失败**不阻断启动**：界面进空库/演示态，用户至少能用
                State.Failed("${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /**
     * 取走迁移报告（**只取一次**）。
     *
     * 取值即把状态推进到 [State.Skipped]，于是「提示一行」这件事天然只发生一次——
     * 旋转屏幕、Activity 重建都不会重复弹。界面不需要自己记标记。
     */
    fun consumeReport(): String? {
        val current = _state.value
        if (current is State.Done) {
            _state.value = State.Skipped
            return current.report
        }
        return null
    }

    /** 测试用：把状态复位（否则单例会让第二个用例永远看到上一次的结论）。 */
    fun resetForTest() {
        _state.value = State.Idle
    }
}
