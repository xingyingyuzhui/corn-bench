package dev.corn.bench

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.corn.ui.mode.BenchMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 外壳启动时的模式状态。[Loading] 用来避免“已经选过模式却先闪一下选择页”。 */
sealed interface ModeState {
    data object Loading : ModeState
    data object Unset : ModeState
    data class Selected(val mode: BenchMode) : ModeState
}

/**
 * 上次使用的模式。Unset = 第一次启动（显示全屏模式选择页）。
 *
 * 文件与两个功能模块的分开：`bench_ui`（外壳）、`flashing_ui`（烧录）、`ui_settings`（上位机）。
 * 存 [BenchMode.key]（"modbus" / "flashing"），不存 ordinal：枚举顺序以后变了也不会读错。
 */
interface ModeStore {
    val state: StateFlow<ModeState>

    suspend fun setMode(mode: BenchMode)
}

private val Context.benchModeStore by preferencesDataStore(name = "bench_ui")

class DataStoreModeStore(
    context: Context,
    scope: CoroutineScope,
) : ModeStore {
    private val store = context.applicationContext.benchModeStore

    override val state: StateFlow<ModeState> = store.data
        .map { preferences ->
            BenchMode.fromKey(preferences[KEY_LAST_MODE])?.let(ModeState::Selected) ?: ModeState.Unset
        }
        .stateIn(scope, SharingStarted.Eagerly, ModeState.Loading)

    override suspend fun setMode(mode: BenchMode) {
        store.edit { it[KEY_LAST_MODE] = mode.key }
    }

    private companion object {
        val KEY_LAST_MODE = stringPreferencesKey("last_mode")
    }
}

/** 只给预览 / 测试用的内存实现。 */
class MemoryModeStore(initial: BenchMode? = null) : ModeStore {
    private val mutable = MutableStateFlow<ModeState>(
        initial?.let(ModeState::Selected) ?: ModeState.Unset,
    )
    override val state: StateFlow<ModeState> = mutable

    override suspend fun setMode(mode: BenchMode) {
        mutable.value = ModeState.Selected(mode)
    }
}
