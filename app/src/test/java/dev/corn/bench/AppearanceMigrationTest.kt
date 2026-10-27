package dev.corn.bench

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import dev.corn.ui.theme.BenchAccent
import dev.corn.ui.theme.BenchThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 外观迁移（第一期 `ui_settings` → 外壳 `bench_ui`）与外壳自己的读写。
 *
 * 每个用例一份临时 DataStore 文件，不共用全局 `benchUiStore` 单例，用例之间互不影响。
 * 旧值通过 `legacy` 参数直接给，不必真去写上位机模块的 `ui_settings`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppearanceMigrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun newStore(name: String = "bench_ui"): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("$name.preferences_pb") }

    private suspend fun DataStore<Preferences>.snapshot() = data.first()

    @Test
    fun onlyLegacyThemeIsMigrated() = runBlocking {
        val store = newStore()
        migrateLegacyAppearance(store) { "LIGHT" to null }

        val prefs = store.snapshot()
        assertEquals("LIGHT", prefs[BenchAppearanceStore.KEY_THEME])
        assertNull(prefs[BenchAppearanceStore.KEY_ACCENT])
        assertEquals(true, prefs[BenchAppearanceStore.KEY_MIGRATED])
    }

    @Test
    fun onlyLegacyAccentIsMigrated() = runBlocking {
        val store = newStore()
        migrateLegacyAppearance(store) { null to "BLUE" }

        val prefs = store.snapshot()
        assertNull(prefs[BenchAppearanceStore.KEY_THEME])
        assertEquals("BLUE", prefs[BenchAppearanceStore.KEY_ACCENT])
        assertEquals(true, prefs[BenchAppearanceStore.KEY_MIGRATED])
    }

    /** 第一期上位机的 AMBER 在外壳里没有同名项，显式映射成玉米黄。 */
    @Test
    fun legacyAmberBecomesCorn() = runBlocking {
        val store = newStore()
        migrateLegacyAppearance(store) { "DARK" to "AMBER" }

        val prefs = store.snapshot()
        assertEquals("DARK", prefs[BenchAppearanceStore.KEY_THEME])
        assertEquals(BenchAccent.CORN.name, prefs[BenchAppearanceStore.KEY_ACCENT])
    }

    /**
     * 评审 E 指出的 bug：旧数据只有 accent 时 `theme_mode` 永远不写，原来的判定会每次冷启动都重新迁移，
     * 把用户后来只改的强调色覆盖回旧值。
     */
    @Test
    fun laterUserChoiceIsNotOverwrittenOnNextStart() = runBlocking {
        val store = newStore()
        val legacy: suspend () -> Pair<String?, String?>? = { null to "BLUE" }
        migrateLegacyAppearance(store, legacy)
        store.edit { it[BenchAppearanceStore.KEY_ACCENT] = BenchAccent.TEAL.name }

        // 第二次冷启动。
        migrateLegacyAppearance(store, legacy)

        val prefs = store.snapshot()
        assertEquals(BenchAccent.TEAL.name, prefs[BenchAppearanceStore.KEY_ACCENT])
        assertNull(prefs[BenchAppearanceStore.KEY_THEME])
    }

    /** 标记引入前的第二期版本已经写过外壳外观：只补标记，不读旧值、不覆盖。 */
    @Test
    fun existingShellValuesWinOverLegacy() = runBlocking {
        val store = newStore()
        store.edit { it[BenchAppearanceStore.KEY_ACCENT] = BenchAccent.PURPLE.name }
        var legacyRead = false
        migrateLegacyAppearance(store) {
            legacyRead = true
            "DARK" to "BLUE"
        }

        val prefs = store.snapshot()
        assertFalse(legacyRead)
        assertEquals(BenchAccent.PURPLE.name, prefs[BenchAppearanceStore.KEY_ACCENT])
        assertNull(prefs[BenchAppearanceStore.KEY_THEME])
        assertEquals(true, prefs[BenchAppearanceStore.KEY_MIGRATED])
    }

    /** 新安装（没有旧值）也记标记，之后不再去读上位机的 `ui_settings`。 */
    @Test
    fun noLegacyDataStillMarksMigrated() = runBlocking {
        val store = newStore()
        migrateLegacyAppearance(store) { null }
        var calledAgain = false
        migrateLegacyAppearance(store) {
            calledAgain = true
            "DARK" to "BLUE"
        }

        val prefs = store.snapshot()
        assertFalse(calledAgain)
        assertNull(prefs[BenchAppearanceStore.KEY_THEME])
        assertNull(prefs[BenchAppearanceStore.KEY_ACCENT])
        assertEquals(true, prefs[BenchAppearanceStore.KEY_MIGRATED])
    }

    /** 读旧值失败：不挡启动（退回默认外观），也不记标记，下次启动再试。 */
    @Test
    fun legacyFailureFallsBackAndRetriesLater() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = newStore()
        val appearance = BenchAppearanceStore(context, scope, store) { error("ui_settings 读不出来") }
        awaitLoaded(appearance)

        assertEquals(BenchThemeMode.SYSTEM, appearance.state.value.themeMode)
        assertEquals(BenchAccent.CORN, appearance.state.value.accent)
        assertNull(store.snapshot()[BenchAppearanceStore.KEY_MIGRATED])
    }

    /** 首帧等读盘：迁移 / 读盘完成前 `loaded` 为 false，完成后状态已经是盘上的值。 */
    @Test
    fun loadedFlipsOnlyAfterDiskValuesArrive() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = newStore()
        val gate = CompletableDeferred<Pair<String?, String?>?>()
        val appearance = BenchAppearanceStore(context, scope, store) { gate.await() }

        delay(100)
        assertFalse(appearance.loaded.value)

        gate.complete("LIGHT" to "GREEN")
        awaitLoaded(appearance)
        assertEquals(BenchThemeMode.LIGHT, appearance.state.value.themeMode)
        assertEquals(BenchAccent.GREEN, appearance.state.value.accent)
    }

    @Test
    fun appearanceRoundTripsThroughTheShellStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = newStore()
        val first = BenchAppearanceStore(context, scope, store) { null }
        awaitLoaded(first)
        first.setThemeMode(BenchThemeMode.LIGHT)
        first.setAccent(BenchAccent.TEAL)
        withTimeout(10_000) {
            store.data.first {
                it[BenchAppearanceStore.KEY_ACCENT] == BenchAccent.TEAL.name &&
                    it[BenchAppearanceStore.KEY_THEME] == BenchThemeMode.LIGHT.name
            }
        }

        // 新建一个 store 从头读盘：值必须持久化过（不是只活在内存里）。
        val second = BenchAppearanceStore(context, scope, store) { "DARK" to "BLUE" }
        awaitLoaded(second)
        assertEquals(BenchThemeMode.LIGHT, second.state.value.themeMode)
        assertEquals(BenchAccent.TEAL, second.state.value.accent)
    }

    private suspend fun awaitLoaded(appearance: BenchAppearanceStore) {
        withTimeout(10_000) {
            while (!appearance.loaded.value) delay(10)
        }
        assertTrue(appearance.loaded.value)
    }
}
