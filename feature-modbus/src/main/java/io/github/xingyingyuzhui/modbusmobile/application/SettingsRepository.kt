package io.github.xingyingyuzhui.modbusmobile.application

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface SettingsRepository {
    val settings: StateFlow<UiSettings>

    suspend fun update(transform: (UiSettings) -> UiSettings)
}

private val Context.uiSettingsStore by preferencesDataStore(name = "ui_settings")

class DataStoreSettingsRepository(
    context: Context,
    scope: CoroutineScope,
) : SettingsRepository {
    private val store = context.applicationContext.uiSettingsStore

    override val settings: StateFlow<UiSettings> = store.data
        .map { preferences -> preferences.readSettings() }
        .stateIn(scope, SharingStarted.Eagerly, UiSettings())

    override suspend fun update(transform: (UiSettings) -> UiSettings) {
        store.edit { preferences ->
            preferences.writeSettings(transform(preferences.readSettings()))
        }
    }
}

private fun Preferences.readSettings(): UiSettings {
    val map = asMap().entries.associate { (key, value) -> key.name to value.toString() }
    return uiSettingsFrom(map)
}

private fun MutablePreferences.writeSettings(settings: UiSettings) {
    val map = settings.toPreferenceMap()
    map.forEach { (key, value) ->
        if (key in UiSettingKeys.booleans) {
            this[booleanPreferencesKey(key)] = value.toBoolean()
        } else {
            this[stringPreferencesKey(key)] = value
        }
    }
}

/**
 * 第一期的主题 / 强调色存在本模块的 `ui_settings` 里。第二期外观改由外壳统一持有（`bench_ui`），
 * 外壳启动时用这个函数读一次旧值做迁移，之后以外壳为准。
 *
 * 返回 null = 还没写过设置（新安装），不需要迁移。
 */
suspend fun legacyAppearance(context: Context): Pair<String?, String?>? {
    val prefs = context.applicationContext.uiSettingsStore.data.first()
    val theme = prefs[stringPreferencesKey(UiSettingKeys.THEME)] as? String
    val accent = prefs[stringPreferencesKey(UiSettingKeys.ACCENT)] as? String
    if (theme == null && accent == null) return null
    return theme to accent
}
