package dev.corn.bench

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.corn.ui.theme.AppearanceController
import dev.corn.ui.theme.BenchAccent
import dev.corn.ui.theme.BenchAppearanceState
import dev.corn.ui.theme.BenchThemeMode

/** 内存版外观控制器：和外壳的 DataStore 实现同契约，测试不碰磁盘。 */
internal class FakeAppearance(
    initial: BenchAppearanceState = BenchAppearanceState(),
) : AppearanceController {
    private val mutable = mutableStateOf(initial)
    override val state: State<BenchAppearanceState> = mutable

    override fun setThemeMode(mode: BenchThemeMode) {
        mutable.value = mutable.value.copy(themeMode = mode)
    }

    override fun setAccent(accent: BenchAccent) {
        mutable.value = mutable.value.copy(accent = accent)
    }
}
