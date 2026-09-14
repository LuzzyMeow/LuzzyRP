package com.luzzymeow.luzzyrp.data.settings

import android.content.Context
import androidx.core.content.edit

/**
 * 应用设置持久化（P4-B-3.3）。
 *
 * ## 为什么设置放 SharedPreferences，而用户内容放 Room
 *
 * 不是随手选的：**主题必须在首帧之前就知道**。Compose 的第一次组合就要决定亮暗与色板，
 * 而 Room 是协程式 API（读一次要走 IO 线程 + 挂起），拿到值之前界面已经画出去了 —— 那就是
 * 一次可看见的「先亮后暗」闪变。SharedPreferences 首次读是同步的（加载完在内存里），
 * 正好匹配这个需求。
 *
 * 所以分工是：**用户内容（角色/会话/记忆…）→ Room；应用设置（主题…）→ SharedPreferences**。
 * 两边都不是「统一存一处」的教条，各取所长。
 *
 * （旧版 `luzzy_transport` 里的供应商配置继续由 [com.luzzymeow.luzzyrp.chat.TransportStore]
 * 持有：它同样需要同步读，且自带打码/不回显的展示约定。旧数据里的供应商配置通过
 * [SettingsBootstrap] 一次性搬进去。）
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        themeMode = prefs.getString(KEY_THEME_MODE, null)
            ?.let { raw -> ThemeMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } },
        fontScale = prefs.getFloat(KEY_FONT_SCALE, Float.NaN)
            .takeUnless { it.isNaN() || it <= 0f },
        styleFilterEnabled = prefs.getBoolean(KEY_STYLE_FILTER, true),
    )

    fun save(settings: AppSettings) = prefs.edit {
        if (settings.themeMode == null) remove(KEY_THEME_MODE) else putString(KEY_THEME_MODE, settings.themeMode.name)
        val fontScale = settings.fontScale
        if (fontScale == null) remove(KEY_FONT_SCALE) else putFloat(KEY_FONT_SCALE, fontScale)
        putBoolean(KEY_STYLE_FILTER, settings.styleFilterEnabled)
    }

    /** 一次性旧数据搬运是否已做过（防止每次启动都用旧值覆盖用户当前选择）。 */
    var legacyImported: Boolean
        get() = prefs.getBoolean(KEY_LEGACY_IMPORTED, false)
        set(value) = prefs.edit { putBoolean(KEY_LEGACY_IMPORTED, value) }

    private companion object {
        const val PREFS_NAME = "luzzy_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_STYLE_FILTER = "style_filter_enabled"
        const val KEY_LEGACY_IMPORTED = "legacy_imported"
    }
}
