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
 * 那是比不迁移更烦人的 bug。所以用一个 `legacy_imported` 标记把它钉成一次性动作。
 *
 * ## 优先级：**新版自己的值优先，旧值只补空缺**
 *
 * 逐个字段判断「现在有没有值」：已有值就保留（用户已经在新版里设过了），空的才用旧值填。
 * 这样「升级带着配置过来」和「用户后来改的不会被旧值顶掉」两件事同时成立。
 *
 * ## 供应商配置为什么写进 [TransportStore]
 *
 * 因为运行时读它的是聊天页（同步、首帧就要知道「配没配」），见 [SettingsStore] 的类注释。
 * 这里只是把旧值灌进去，不改变读取方。
 */
object SettingsBootstrap {

    private const val TAG = "LuzzySettings"

    /** 返回一行给日志/报告看的说明；无事可做时返回 null。 */
    suspend fun importOnce(context: Context, store: LuzzyStore): String? {
        val settingsStore = SettingsStore(context)
        if (settingsStore.legacyImported) return null

        val blob = store.json(LuzzyStore.KEY_SETTINGS)
        val legacy = LegacySettingsReader.read(blob)

        val applied = mutableListOf<String>()

        // 1) 主题模式：新版没设过（= 跟随系统）才用旧值
        val current = settingsStore.load()
        if (current.themeMode == null && legacy.themeMode != null) {
            settingsStore.save(current.copy(themeMode = legacy.themeMode))
            applied += "主题=${legacy.themeMode}"
        }

        // 2) 供应商：新版完全没配才灌（逐字段判断会让「只填了一半」的中间态很难解释）
        val transport = TransportStore(context)
        val existing = transport.load()
        if (!existing.configured && legacy.hasProvider) {
            transport.save(
                TransportConfig(
                    baseUrl = legacy.providerBaseUrl.orEmpty(),
                    apiKey = legacy.providerApiKey.orEmpty(),
                    model = legacy.providerModel.orEmpty(),
                    toolsEnabled = existing.toolsEnabled,
                ),
            )
            applied += "供应商配置（模型 ${legacy.providerModel}）"
        }

        settingsStore.legacyImported = true
        legacy.notes.forEach { Log.i(TAG, "旧设置提示：$it") }
        if (legacy.fontSize != null) {
            // 用户可调字号属字体排版，按硬性规定 9 需要单独走设计流程；先如实记录读到过
            Log.i(TAG, "旧设置里的字号为 ${legacy.fontSize}（尚未接入：需先过设计流程）")
        }
        if (applied.isEmpty()) {
            Log.i(TAG, "旧设置里没有可搬的东西（或新版已有值），已记标记")
            return null
        }
        val summary = "已搬来旧设置：" + applied.joinToString(" · ")
        Log.i(TAG, summary)
        return summary
    }

    /** 测试用：复位「已搬运」标记。 */
    fun resetImportedFlag(context: Context) {
        SettingsStore(context).legacyImported = false
    }
}
