package dev.corn.bench

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.corn.ui.theme.AppearanceController
import dev.corn.ui.theme.BenchAccent
import dev.corn.ui.theme.BenchAppearanceState
import dev.corn.ui.theme.BenchThemeMode
import kotlinx.coroutines.CoroutineScope
import io.github.xingyingyuzhui.modbusmobile.application.legacyAppearance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 外壳级外观的持久化（`bench_ui` DataStore，和 `last_mode` 同一个文件）。
 *
 * 第二期起深浅色 / 强调色对用户开放；放在外壳而不是某个 feature 里，是因为两个模式要共用同一套外观，
 * 而且冷启动停在烧录模式时不能为了读主题去创建上位机的容器（那会连带初始化 USB 仓库和 Room）。
 */
class BenchAppearanceStore(
    private val context: Context,
    private val scope: CoroutineScope,
) : AppearanceController {
    private val store = context.applicationContext.benchUiStore
    private val _state = mutableStateOf(BenchAppearanceState())

    override val state: State<BenchAppearanceState> = _state

    init {
        scope.launch {
            migrateLegacyAppearance()
            store.data
                .map { prefs ->
                    BenchAppearanceState(
                        themeMode = prefs[KEY_THEME]?.let { name ->
                            BenchThemeMode.entries.firstOrNull { it.name == name }
                        } ?: BenchThemeMode.SYSTEM,
                        accent = prefs[KEY_ACCENT]?.let { name ->
                            BenchAccent.entries.firstOrNull { it.name == name }
                        } ?: BenchAccent.DEFAULT,
                    )
                }
                .collect { _state.value = it }
        }
    }

    override fun setThemeMode(mode: BenchThemeMode) {
        _state.value = _state.value.copy(themeMode = mode)
        scope.launch { store.edit { it[KEY_THEME] = mode.name } }
    }

    override fun setAccent(accent: BenchAccent) {
        _state.value = _state.value.copy(accent = accent)
        scope.launch { store.edit { it[KEY_ACCENT] = accent.name } }
    }

    /**
     * 第一期把主题 / 强调色存在上位机的 `ui_settings` 里（当时界面隐藏了这两项，但旧数据可能有）。
     * 外壳第一次启动时读一次迁移过来，之后以外壳为准；读不到（新安装 / 解析失败）就跳过，不挡启动。
     */
    private suspend fun migrateLegacyAppearance() {
        if (store.data.first().contains(KEY_THEME)) return
        val legacy = runCatching { legacyAppearance(context) }.getOrNull() ?: return
        val (themeName, accentName) = legacy
        val theme = themeName?.let { name -> BenchThemeMode.entries.firstOrNull { it.name == name } }
        val accent = accentName?.let { name -> BenchAccent.entries.firstOrNull { it.name == name } }
        if (theme == null && accent == null) return
        store.edit { prefs ->
            theme?.let { prefs[KEY_THEME] = it.name }
            accent?.let { prefs[KEY_ACCENT] = it.name }
        }
    }

    internal companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_ACCENT = stringPreferencesKey("accent")
    }
}

internal val Context.benchUiStore by preferencesDataStore(name = "bench_ui")
