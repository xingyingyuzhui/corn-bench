@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T-20261018-and-07（CL-02）§7.2 按钮 loading 显示时机：300ms 内结束的不显示进度圈；一旦显示至少 500ms。
 * 纯 JVM（[gateLoading] 不碰 Compose / android.*），不受 B-3 影响。
 */
class LoadingGateTest {
    /** 记录 (虚拟时间 ms, 显示/隐藏)。 */
    private fun TestScope.track(loading: MutableStateFlow<Boolean>): MutableList<Pair<Long, Boolean>> {
        val events = mutableListOf<Pair<Long, Boolean>>()
        backgroundScope.launch { gateLoading(loading) { events += testScheduler.currentTime to it } }
        testScheduler.runCurrent()
        return events
    }

    private fun TestScope.at(ms: Long) {
        testScheduler.advanceTimeBy(ms - testScheduler.currentTime)
        testScheduler.runCurrent()
    }

    @Test
    fun finishingWithin300msNeverShowsTheSpinner() = runTest {
        val loading = MutableStateFlow(false)
        val events = track(loading)
        loading.value = true
        at(250)
        loading.value = false
        at(2_000)
        assertEquals(emptyList<Pair<Long, Boolean>>(), events)
    }

    @Test
    fun longLoadingShowsAfter300msAndHidesWhenDone() = runTest {
        val loading = MutableStateFlow(false)
        val events = track(loading)
        loading.value = true
        at(1_500)
        loading.value = false
        at(3_000)
        assertEquals(listOf(300L to true, 1_500L to false), events)
    }

    @Test
    fun onceShownStaysAtLeast500ms() = runTest {
        val loading = MutableStateFlow(false)
        val events = track(loading)
        loading.value = true
        at(400) // 300 出现
        loading.value = false
        at(3_000)
        assertEquals("300 出现，最早 800 才消失", listOf(300L to true, 800L to false), events)
    }

    @Test
    fun loadingAgainDuringTheMinimumKeepsItShownWithoutFlicker() = runTest {
        val loading = MutableStateFlow(false)
        val events = track(loading)
        loading.value = true
        at(400)
        loading.value = false
        at(600)
        loading.value = true
        at(2_000)
        loading.value = false
        at(3_000)
        assertEquals(listOf(300L to true, 2_000L to false), events)
    }
}
