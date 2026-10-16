package io.github.xingyingyuzhui.modbusmobile.ui.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.Parity
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.rtu.BusTopology
import io.github.xingyingyuzhui.modbusmobile.core.rtu.EchoMode
import kotlinx.coroutines.launch

class ConnectionViewModel(private val session: SessionManager) : ViewModel() {
    val state = session.state
    val devices = session.usbDevices
    val echoDetected = session.echoDetected
    var baud by mutableStateOf(9600)
    var dataBits by mutableStateOf(8)
    var stopBits by mutableStateOf(1)
    var parity by mutableStateOf(Parity.NONE)
    var role by mutableStateOf(Role.MASTER)
    var topology by mutableStateOf(BusTopology.POINT_TO_POINT)
    var echoMode by mutableStateOf(EchoMode.OFF)
    var selected by mutableStateOf<ConnectionTarget>(ConnectionTarget.Demo)

    fun connect() {
        viewModelScope.launch {
            session.connect(
                selected,
                SerialSettings(
                    baudRate = baud,
                    dataBits = dataBits,
                    parity = parity,
                    stopBits = stopBits,
                    topology = topology,
                    echoMode = echoMode,
                ),
                role,
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch { session.disconnect() }
    }
}
