package dev.corn.bench

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.corn.flashing.FlashingFeature
import dev.corn.ui.mode.BenchMode
import io.github.xingyingyuzhui.modbusmobile.ModbusFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `last_mode` 的持久化映射，以及“守卫本身不会把两个模式的懒加载容器创建出来”。
 *
 * 外壳的交互用 [BenchShellTest]（内存 ModeStore + 假守卫）；这里只验持久化与懒加载两条边界。
 * DataStore 的真实文件读写要自己的 IO 调度器，不放进 `runTest` 的虚拟时间（会被判成瞬间超时），
 * 所以单独一条用真实调度 + 真实超时；Robolectric 下仍属本机单测，不是实机验证。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ModeStoreTest {
    @Test
    fun dataStoreStartsUnsetThenRoundTripsTheModeKey() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = DataStoreModeStore(context, scope)

            // 第一次启动（文件还没有 last_mode）→ Unset，外壳显示全屏选择页。
            assertEquals(ModeState.Unset, withTimeout(10_000) { store.state.first { it != ModeState.Loading } })

            store.setMode(BenchMode.FLASHING)
            assertEquals(
                ModeState.Selected(BenchMode.FLASHING),
                withTimeout(10_000) { store.state.first { it == ModeState.Selected(BenchMode.FLASHING) } },
            )

            // 存的是 key 而不是 ordinal：换回上位机也读得回来。
            store.setMode(BenchMode.MODBUS)
            assertEquals(
                ModeState.Selected(BenchMode.MODBUS),
                withTimeout(10_000) { store.state.first { it == ModeState.Selected(BenchMode.MODBUS) } },
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun storedKeyMapsByModeKeyNotByOrdinal() {
        assertEquals(BenchMode.MODBUS, BenchMode.fromKey("modbus"))
        assertEquals(BenchMode.FLASHING, BenchMode.fromKey("flashing"))
        assertNull(BenchMode.fromKey("nope"))
        assertNull(BenchMode.fromKey(null))
    }

    @Test
    fun memoryStoreFallsBackToUnsetForUnknownOrMissingMode() {
        assertEquals(ModeState.Unset, MemoryModeStore(null).state.value)
        assertEquals(ModeState.Selected(BenchMode.MODBUS), MemoryModeStore(BenchMode.MODBUS).state.value)
    }

    /**
     * 冷启动停在哪个模式，就不该初始化另一个模式的 DataStore / USB 仓库 / Room。
     * 守卫只是查询状态：没有创建过容器时直接返回“可以离开”，不会顺手创建。
     */
    @Test
    fun defaultGuardQueriesWithoutCreatingContainers() = runTest {
        assertFalse(ModbusFeature.isCreated)
        assertFalse(FlashingFeature.isCreated)

        assertNull(DefaultModeGuard.blockedReason(BenchMode.MODBUS))
        assertNull(DefaultModeGuard.blockedReason(BenchMode.FLASHING))
        // 可观察版本（切换面板订阅的那一路）同样不创建容器。
        assertNull(DefaultModeGuard.blockedReasonFlow(BenchMode.MODBUS).first())
        assertNull(DefaultModeGuard.blockedReasonFlow(BenchMode.FLASHING).first())
        assertTrue(DefaultModeGuard.release(BenchMode.MODBUS))
        assertTrue(DefaultModeGuard.release(BenchMode.FLASHING))

        assertFalse("查询守卫不应创建上位机容器", ModbusFeature.isCreated)
        assertFalse("查询守卫不应创建烧录容器", FlashingFeature.isCreated)
    }
}
