package dev.corn.flashing

import dev.corn.flashing.storage.BatchPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外壳切换守卫的烧录侧：[flashingLeaveBlockedReason] / [releaseFlashingUsb]。
 * 外壳 `BenchShellTest` 用的是假守卫，这里锁定真实判定：各忙态的提示文案、超时返回 false。
 */
class FlashingLeaveGuardTest {
    @Test
    fun idleCanLeave() {
        assertNull(flashingLeaveBlockedReason(running = false, batchPhase = null))
    }

    @Test
    fun singleFlashRunningBlocks() {
        assertEquals("正在烧录：等这一片完成后再切换", flashingLeaveBlockedReason(running = true, batchPhase = null))
        // 单片烧录优先于批次提示。
        assertEquals("正在烧录：等这一片完成后再切换", flashingLeaveBlockedReason(running = true, batchPhase = BatchPhase.PAUSED))
    }

    @Test
    fun everyBatchPhaseBlocksWithItsOwnMessage() {
        assertEquals("批次已暂停但未结束：请先在工作台结束批次", flashingLeaveBlockedReason(false, BatchPhase.PAUSED))
        assertEquals("批次已烧完但还没结束：请先在工作台结束批次", flashingLeaveBlockedReason(false, BatchPhase.FINISHED))
        assertEquals("批量烧录进行中：请先在工作台结束批次", flashingLeaveBlockedReason(false, BatchPhase.WAITING_BOARD))
        assertEquals("批量烧录进行中：请先在工作台结束批次", flashingLeaveBlockedReason(false, BatchPhase.FLASHING))
        BatchPhase.entries.forEach { assertNotNull("$it 必须拦截", flashingLeaveBlockedReason(false, it)) }
    }

    @Test
    fun releaseRunsLeaveHooksAndSucceedsWhenNotProbing() = runTest {
        var hooks = 0
        val ok = releaseFlashingUsb(
            leaveHooks = listOf({ hooks++ }, { hooks++ }),
            blockedReason = { null },
            probing = MutableStateFlow(false),
            timeoutMillis = 5_000,
        )
        assertTrue(ok)
        assertEquals(2, hooks)
    }

    /** 离开钩子跑完仍有任务（烧录 / 批次）就不切换，也不去等识别。 */
    @Test
    fun releaseFailsWhileStillBlocked() = runTest {
        var hooks = 0
        val ok = releaseFlashingUsb(
            leaveHooks = listOf({ hooks++ }),
            blockedReason = { "正在烧录：等这一片完成后再切换" },
            probing = MutableStateFlow(false),
            timeoutMillis = 5_000,
        )
        assertFalse(ok)
        assertEquals("钩子仍要先跑（停掉等待下一块板）", 1, hooks)
    }

    @Test
    fun releaseWaitsForProbingToFinish() = runTest {
        val probing = MutableStateFlow(true)
        var result: Boolean? = null
        launch { result = releaseFlashingUsb(emptyList(), { null }, probing, timeoutMillis = 5_000) }
        advanceTimeBy(1_000)
        assertNull("识别还没结束，不应返回", result)
        probing.value = false
        advanceTimeBy(1)
        testScheduler.advanceUntilIdle()
        assertEquals(true, result)
    }

    /** 识别芯片一直不结束：超时返回 false，外壳停在原模式。 */
    @Test
    fun releaseTimesOutWhileProbing() = runTest {
        val ok = releaseFlashingUsb(emptyList(), { null }, MutableStateFlow(true), timeoutMillis = 5_000)
        assertFalse(ok)
    }
}
