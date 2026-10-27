package dev.corn.bench

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import dev.corn.ui.theme.BenchAccent
import dev.corn.ui.theme.BenchThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 外观持久化：`bench_ui` 里存 `theme_mode` / `accent`，重新创建 store 后要读得回来。
 *
 * 第一期的主题存在上位机模块的 `ui_settings` 里，迁移逻辑在 `BenchAppearanceStore` 的 init；
 * 这里验证的是外壳自己的读写（迁移失败也不能挡住启动）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppearanceMigrationTest {
    @Test
    fun appearanceRoundTripsThroughTheShellStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = BenchAppearanceStore(context, scope)
            withTimeout(10_000) {
                first.state.let { state ->
                    while (state.value.themeMode != BenchThemeMode.SYSTEM) Thread.sleep(10)
                }
            }
            first.setThemeMode(BenchThemeMode.LIGHT)
            first.setAccent(BenchAccent.TEAL)
            withTimeout(10_000) {
                while (first.state.value.accent != BenchAccent.TEAL) Thread.sleep(10)
            }

            // 新建一个 store 从头读盘：值必须持久化过（不是只活在内存里）。
            val second = BenchAppearanceStore(context, scope)
            val restored = withTimeout(10_000) {
                second.state.let { state ->
                    while (state.value.accent != BenchAccent.TEAL || state.value.themeMode != BenchThemeMode.LIGHT) {
                        Thread.sleep(10)
                    }
                    state.value
                }
            }
            assertEquals(BenchThemeMode.LIGHT, restored.themeMode)
            assertEquals(BenchAccent.TEAL, restored.accent)
        } finally {
            scope.cancel()
        }
    }

    /** 还没设过外观时退回“跟随系统 + 玉米”，不崩、不写脏值。 */
    @Test
    fun missingKeysFallBackToDefaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.benchUiStore.edit { prefs ->
            prefs.remove(BenchAppearanceStore.KEY_THEME)
            prefs.remove(BenchAppearanceStore.KEY_ACCENT)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val appearance = BenchAppearanceStore(context, scope)
            assertEquals(BenchThemeMode.SYSTEM, appearance.state.value.themeMode)
            assertEquals(BenchAccent.CORN, appearance.state.value.accent)
        } finally {
            scope.cancel()
        }
    }
}
