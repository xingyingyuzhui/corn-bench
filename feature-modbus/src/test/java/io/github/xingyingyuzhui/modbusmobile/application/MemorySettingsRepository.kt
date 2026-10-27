package io.github.xingyingyuzhui.modbusmobile.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MemorySettingsRepository(initial: UiSettings = UiSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<UiSettings> = state.asStateFlow()

    override suspend fun update(transform: (UiSettings) -> UiSettings) {
        state.value = transform(state.value)
    }
}
