package com.luzzymeow.luzzyrp.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.luzzymeow.luzzyrp.core.common.JsonInstant
import com.luzzymeow.luzzyrp.core.model.BuiltinProviders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.luzzyDataStore by preferencesDataStore(name = "luzzy_settings")

/**
 * 设置单一真源（DataStore）。
 *
 * 模式与 rikkahub PreferencesStore 一致：Settings 整体 JSON 序列化为一个
 * preference；settingsFlow 永不发射错误（IO 异常回退默认值），UI 侧可直接
 * collectAsStateWithLifecycle。所有修改走 [update] 函数式通道，避免并发覆写。
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val SETTINGS = stringPreferencesKey("settings_json")
    }

    /** 非挂起快照（AppLogger 等同步读取方使用；首次读到默认值）。 */
    @Volatile
    var cached: Settings = defaultSettings()
        private set

    val settingsFlow: Flow<Settings> = context.luzzyDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            prefs[Keys.SETTINGS]?.let { json ->
                runCatching { JsonInstant.decodeFromString(Settings.serializer(), json) }
                    .getOrElse { Settings() }
            } ?: Settings()
        }
        .map { settings ->
            cached = settings
            settings
        }

    suspend fun update(transform: (Settings) -> Settings) {
        context.luzzyDataStore.edit { prefs ->
            val current = prefs[Keys.SETTINGS]?.let { json ->
                runCatching { JsonInstant.decodeFromString(Settings.serializer(), json) }
                    .getOrElse { Settings() }
            } ?: defaultSettings()
            prefs[Keys.SETTINGS] = JsonInstant.encodeToString(Settings.serializer(), transform(current))
        }
    }

    companion object {
        /** 首次启动的默认设置：注入内置供应商档案。 */
        fun defaultSettings(): Settings = Settings(
            providers = BuiltinProviders.defaults(),
            currentProviderId = BuiltinProviders.DEEPSEEK.id,
            currentModelId = "deepseek-v4-pro",
        )
    }
}
