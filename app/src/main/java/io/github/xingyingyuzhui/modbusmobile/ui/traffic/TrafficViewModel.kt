package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrafficViewModel(session: SessionManager) : ViewModel() {
    private val history = MutableStateFlow<List<TrafficEvent>>(emptyList())
    val events: StateFlow<List<TrafficEvent>> = history.asStateFlow()

    init {
        viewModelScope.launch {
            session.traffic.collect { event ->
                history.update { current -> (current + event).takeLast(1_000) }
            }
        }
    }
}
