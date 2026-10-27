package dev.corn.bench

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
 *
 * [store] / [legacy] 默认是真实的 `bench_ui` 与第一期 `ui_settings`；测试传各自独立的临时 DataStore。
 */
class BenchAppearanceStore(
    context: Context,
    private val scope: CoroutineScope,
    private val store: DataStore<Preferences> = context.applicationContext.benchUiStore,
    private val legacy: suspend () -> Pair<String?, String?>? = { legacyAppearance(context.applicationContext) },
) : AppearanceController {
    private val _state = mutableStateOf(BenchAppearanceState())
    private val _loaded = mutableStateOf(false)

    override val state: State<BenchAppearanceState> = _state

    /**
     * 第一次读盘（含迁移）完成前为 false。外壳在此之前不画内容，只露出跟随系统的窗口底色，
     * 避免用户选了和系统相反的主题时，冷启动先按默认值画一帧再跳色。
     */
    val loaded: State<Boolean> = _loaded

    init {
        scope.launch {
            runCatching { migrateLegacyAppearance(store, legacy) }
            store.data
                .map { prefs -> prefs.toAppearance() }
                .collect {
                    _state.value = it
                    _loaded.value = true
                }
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

    internal companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_ACCENT = stringPreferencesKey("accent")

        /** 迁移只做一次：不管旧数据有几项、能不能映射，做过就记上，之后以外壳为准。 */
        val KEY_MIGRATED = booleanPreferencesKey("appearance_migrated")
    }
}

internal fun Preferences.toAppearance() = BenchAppearanceState(
    themeMode = this[BenchAppearanceStore.KEY_THEME]
        ?.let { name -> BenchThemeMode.entries.firstOrNull { it.name == name } }
        ?: BenchThemeMode.SYSTEM,
    accent = this[BenchAppearanceStore.KEY_ACCENT]
        ?.let { name -> BenchAccent.entries.firstOrNull { it.name == name } }
        ?: BenchAccent.DEFAULT,
)

/**
 * 第一期上位机 `AccentColor` 名 → 外壳 [BenchAccent]。
 * 旧的 AMBER（琥珀）在外壳里没有对应项，品牌上最接近的是玉米黄，显式映射成 CORN；其他同名直接对应。
 */
internal fun legacyAccentToBench(name: String): BenchAccent? = when (name) {
    "AMBER" -> BenchAccent.CORN
    else -> BenchAccent.entries.firstOrNull { it.name == name }
}

/**
 * 第一期把主题 / 强调色存在上位机的 `ui_settings` 里（当时界面隐藏了这两项，但旧数据可能有）。
 * 外壳第一次启动时读一次迁移过来，之后以外壳为准。
 *
 * 判定"迁移过"用独立的 [BenchAppearanceStore.KEY_MIGRATED]，不能拿 `theme_mode` 在不在来判断：
 * 旧数据只有 accent 时 `theme_mode` 永远不会写入，每次冷启动都会重新迁移，把用户后来的选择覆盖掉。
 * 已经有外壳外观值（标记引入前的第二期版本写的）也视为迁移过，只补标记、不覆盖。
 * 读旧值抛异常时不记标记，下次再试；旧值为空（新安装）直接记标记。
 */
internal suspend fun migrateLegacyAppearance(
    store: DataStore<Preferences>,
    legacy: suspend () -> Pair<String?, String?>?,
) {
    val current = store.data.first()
    if (current[BenchAppearanceStore.KEY_MIGRATED] == true) return
    if (current.contains(BenchAppearanceStore.KEY_THEME) || current.contains(BenchAppearanceStore.KEY_ACCENT)) {
        store.edit { it[BenchAppearanceStore.KEY_MIGRATED] = true }
        return
    }
    val old = legacy()
    val theme = old?.first?.let { name -> BenchThemeMode.entries.firstOrNull { it.name == name } }
    val accent = old?.second?.let(::legacyAccentToBench)
    store.edit { prefs ->
        theme?.let { prefs[BenchAppearanceStore.KEY_THEME] = it.name }
        accent?.let { prefs[BenchAppearanceStore.KEY_ACCENT] = it.name }
        prefs[BenchAppearanceStore.KEY_MIGRATED] = true
    }
}

internal val Context.benchUiStore by preferencesDataStore(name = "bench_ui")
