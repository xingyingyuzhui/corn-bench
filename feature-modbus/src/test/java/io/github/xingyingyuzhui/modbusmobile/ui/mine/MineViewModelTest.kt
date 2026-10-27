package io.github.xingyingyuzhui.modbusmobile.ui.mine

import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.TimeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MineViewModelTest {
    @Test
    fun editsReachTheRepository() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = MemorySettingsRepository()
            val model = MineViewModel(repository)
            model.setTheme(ThemeMode.LIGHT)
            model.setKeepScreen(true)
            model.setVibration(true)
            model.setTime(TimeStyle.CLOCK)
            advanceUntilIdle()
            val settings = repository.settings.value
            assertEquals(ThemeMode.LIGHT, settings.theme)
            assertTrue(settings.keepScreenOn)
            assertTrue(settings.vibration)
            assertEquals(TimeStyle.CLOCK, settings.timeStyle)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun securityAndCommDefaultsAreStored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = MemorySettingsRepository()
            val model = MineViewModel(repository)
            model.setLogCrcDim(false)
            model.setCoilExtraWarning(false)
            model.setDefaultTimeout(2_000)
            model.setDefaultInterval(40)
            model.setDefaultBroadcastWait(0)
            model.setDefaultEcho(io.github.xingyingyuzhui.modbusmobile.application.EchoMode.ON)
            advanceUntilIdle()
            val s = repository.settings.value
            assertEquals(false, s.logCrcDim)
            assertEquals(false, s.coilExtraWarning)
            assertEquals(2_000L, s.defaultResponseTimeoutMs)
            assertEquals(40L, s.defaultInterRequestMs)
            assertEquals(0L, s.defaultBroadcastWaitMs)
            assertEquals(io.github.xingyingyuzhui.modbusmobile.application.EchoMode.ON, s.defaultEcho)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun parseMsAcceptsOnlyIntegersInRange() {
        assertEquals(1_000L, MineViewModel.parseMs(" 1000 ", MineViewModel.TimeoutRange))
        assertEquals(null, MineViewModel.parseMs("0", MineViewModel.TimeoutRange))
        assertEquals(null, MineViewModel.parseMs("120001", MineViewModel.TimeoutRange))
        assertEquals(null, MineViewModel.parseMs("1.5", MineViewModel.TimeoutRange))
        assertEquals(null, MineViewModel.parseMs("", MineViewModel.TimeoutRange))
        assertEquals(0L, MineViewModel.parseMs("0", MineViewModel.BroadcastRange))
        assertEquals(120_000L, MineViewModel.parseMs("120000", MineViewModel.BroadcastRange))
    }
}
