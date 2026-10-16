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
}
