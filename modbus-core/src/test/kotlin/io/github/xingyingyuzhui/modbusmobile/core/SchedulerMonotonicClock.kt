@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.core

import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import kotlinx.coroutines.test.TestCoroutineScheduler

class SchedulerMonotonicClock(
    private val scheduler: TestCoroutineScheduler,
) : MonotonicClock {
    override fun nowNs(): Long = scheduler.currentTime * 1_000_000L
}
