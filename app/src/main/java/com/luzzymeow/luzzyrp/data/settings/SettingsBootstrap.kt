package com.luzzymeow.luzzyrp.data.settings

import android.content.Context
import android.util.Log
import com.luzzymeow.luzzyrp.chat.TransportConfig
import com.luzzymeow.luzzyrp.chat.TransportStore
import com.luzzymeow.luzzyrp.data.store.LuzzyStore

/**
 * 设置搬运（P4-B-3.3）：把旧数据里的**应用设置**一次性接过来。
 *
 * ## 为什么是「一次性」而不是「每次启动都对账」
 *
 * 旧值只在升级后有意义。若每次启动都拿旧值去覆盖，用户在新版里改过的选择会在下次启动被打回去——
 * 那是比不迁移更烦人的 bug。
 *
 * ## 优先级：**新版自己的值优先，旧值只补空缺**
 *
 * 逐个字段判断「现在有没有值」：已有值就保留（用户已经在新版里设过了），空的才用旧值填。
 * 这样「升级带着配置过来」和「用户后来改的不会被旧值顶掉」两件事同时成立。
 *
 * ## ⚠️ 为什么「标记」不再是唯一的开关（2026-09-13 真机实测修正）
 *
 * 原实现第一行就是 `if (legacyImported) return`，并在**没读到旧设置时也把标记种下**。
 * 真机上正好撞上这个组合：首次打开界面时迁移还没跑完（旧数据 28.6 MB，导出二十来秒），
 * `kv["settings"]` 还是空的 → 标记被种 → 第二次启动迁移成功后 `importOnce` 直接返回 →
 * **用户的供应商配置（Base URL / API Key / 模型）永久搬不过来**，界面一直显示「未配置」。
 *
 * 于是拆成两条互补的规则（由 [plan] 这个纯函数决定，见 `SettingsBootstrapPlanTest`）：
 * 1. **只有真的读到过旧设置才种标记**——「读不到」不等于「没有」，可能只是迁移还没成功；
 * 2. **快路径看「有没有空缺」而不是看标记**（[hasGap]）——标记已种但供应商仍是空的时，
 *    继续尝试补上（这条同时把已经被误种标记的设备**自愈**回来）。
 *
 * 因为两个分支都是「只补空缺」，重复执行天然安全；标记只是省一次 kv 读的优化。
 *
 * ## 供应商配置为什么写进 [TransportStore]
 *
 * 因为运行时读它的是聊天页（同步、首帧就要知道「配没配」），见 [SettingsStore] 的类注释。
 * 这里只是把旧值灌进去，不改变读取方。
 */
object SettingsBootstrap {

    private const val TAG = "LuzzySettings"

    /**
     * 一次搬运的**决定**（纯数据）。
     *
     * [transport] / [themeMode] 为 null 表示「不用灌」（新版已有值，或旧数据里没有）。
     */
    data class Plan(
        val transport: TransportConfig? = null,
        val themeMode: ThemeMode? = null,
        /** 是否该把「已搬运」标记种下（见类注释的两条规则）。 */
        val latch: Boolean = false,
    ) {
        /** 给人看的一行说明（日志/报告）。 */
        val applied: List<String>
            get() = buildList {
                themeMode?.let { add("主题=$it") }
                transport?.let { add("供应商配置（模型 ${it.model}）") }
            }
    }

    /**
     * 决定这次要灌什么。
     *
     * 纯函数（不碰 Context / SharedPreferences）——真机踩到的那两条缺陷必须能被单测钉住，
     * 而 `importOnce` 需要 Context，测不了。
     *
     * @param legacyBlobPresent 迁移进来的 `kv["settings"]` **是否存在**（不是「是否为空对象」）
     * @param alreadyImported 「已搬运」标记当前是否为真
     */
    fun plan(
        legacyBlobPresent: Boolean,
        legacy: LegacySettings,
        existingTransport: TransportConfig,
        currentTheme: ThemeMode?,
        alreadyImported: Boolean = false,
    ): Plan {
        val fillTransport = !existingTransport.configured && legacy.hasProvider
        val fillTheme = currentTheme == null && legacy.themeMode != null
        return Plan(
            transport = if (fillTransport) {
                TransportConfig(
                    baseUrl = legacy.providerBaseUrl.orEmpty(),
                    apiKey = legacy.providerApiKey.orEmpty(),
                    model = legacy.providerModel.orEmpty(),
                    // 工具开关是新版自己的选择，不参与搬运
                    toolsEnabled = existingTransport.toolsEnabled,
                )
            } else {
                null
            },
            themeMode = legacy.themeMode.takeIf { fillTheme },
            // ★ 只有真的读到过旧设置才种标记；已经种着的保持种着。
            latch = alreadyImported || legacyBlobPresent,
        )
    }

    /**
     * 是否还有**空缺可补**——快路径的判据。
     *
     * 刻意不看「已搬运」标记：那个标记可能在旧设置还读不到的时候就被种下了（真机实测）。
     * 「供应商已配 + 主题已设」才说明真的没什么可补了。
     */
    fun hasGap(existing: TransportConfig, currentTheme: ThemeMode?): Boolean =
        !existing.configured || currentTheme == null

    /** 返回一行给日志/报告看的说明；无事可做时返回 null。 */
    suspend fun importOnce(context: Context, store: LuzzyStore): String? {
        val settingsStore = SettingsStore(context)
        val transport = TransportStore(context)

        val existing = transport.load()
        val current = settingsStore.load()

        // 快路径：没有空缺可补 → 连旧设置都不必读（标记只在这里参与，见类注释）
        if (settingsStore.legacyImported && !hasGap(existing, current.themeMode)) return null

        val blob = store.json(LuzzyStore.KEY_SETTINGS)
        val legacy = LegacySettingsReader.read(blob)
        val decision = plan(
            legacyBlobPresent = blob != null,
            legacy = legacy,
            existingTransport = existing,
            currentTheme = current.themeMode,
            alreadyImported = settingsStore.legacyImported,
        )

        decision.transport?.let { transport.save(it) }
        decision.themeMode?.let { settingsStore.save(current.copy(themeMode = it)) }
        if (decision.latch) settingsStore.legacyImported = true

        legacy.notes.forEach { Log.i(TAG, "旧设置提示：$it") }
        if (legacy.fontSize != null) {
            // 用户可调字号属字体排版，按硬性规定 9 需要单独走设计流程；先如实记录读到过
            Log.i(TAG, "旧设置里的字号为 ${legacy.fontSize}（尚未接入：需先过设计流程）")
        }
        if (decision.applied.isEmpty()) {
            Log.i(
                TAG,
                if (blob == null) {
                    "旧设置还读不到（迁移未成功？）——**不种标记**，下次启动继续尝试"
                } else {
                    "旧设置里没有可搬的东西（或新版已有值）"
                },
            )
            return null
        }
        val summary = "已搬来旧设置：" + decision.applied.joinToString(" · ")
        Log.i(TAG, summary)
        return summary
    }

    /** 测试用：复位「已搬运」标记。 */
    fun resetImportedFlag(context: Context) {
        SettingsStore(context).legacyImported = false
    }
}
