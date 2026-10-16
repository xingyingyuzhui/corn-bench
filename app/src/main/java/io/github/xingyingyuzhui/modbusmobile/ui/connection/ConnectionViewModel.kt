package io.github.xingyingyuzhui.modbusmobile.ui.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.application.BusTopology
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.EchoMode
import io.github.xingyingyuzhui.modbusmobile.application.Parity
import io.github.xingyingyuzhui.modbusmobile.application.SerialPreset
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.TimeoutGapValidation
import io.github.xingyingyuzhui.modbusmobile.application.matchingPreset
import io.github.xingyingyuzhui.modbusmobile.application.validateTimeoutGap
import io.github.xingyingyuzhui.modbusmobile.application.wireLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import kotlinx.coroutines.launch

class ConnectionViewModel(private val session: SessionManager) : ViewModel() {
    val state = session.state
    val devices = session.usbDevices
    val echoDetected = session.echoDetected

    var baud by mutableStateOf(9_600)
    var dataBits by mutableStateOf(8)
    var stopBits by mutableStateOf(1)
    var parity by mutableStateOf(Parity.NONE)
    var role by mutableStateOf(Role.MASTER)
    var topology by mutableStateOf(BusTopology.POINT_TO_POINT)
    var echoMode by mutableStateOf(EchoMode.OFF)
    var selected by mutableStateOf<ConnectionTarget>(ConnectionTarget.Demo)
    var timeoutText by mutableStateOf("1000")
    var gapText by mutableStateOf("20")
    var advancedOpen by mutableStateOf(false)
    var customPinned by mutableStateOf(false)

    val preset: SerialPreset?
        get() = if (customPinned) null else matchingPreset(baud, dataBits, parity, stopBits)

    val validation: TimeoutGapValidation
        get() = validateTimeoutGap(timeoutText, gapText)

    val wireLabel: String
        get() = wireLabel(baud, dataBits, parity, stopBits)

    fun selectPreset(preset: SerialPreset) {
        customPinned = false
        baud = preset.baudRate
        dataBits = preset.dataBits
        parity = preset.parity
        stopBits = preset.stopBits
    }

    fun selectCustom() {
        customPinned = true
    }

    fun refresh() {
        session.refreshDevices()
    }

    fun connect() {
        if (!validation.ok) return
        val timeout = timeoutText.trim().toLong()
        val gap = gapText.trim().toLong()
        viewModelScope.launch {
            session.connect(
                selected,
                SerialSettings(
                    baudRate = baud,
                    dataBits = dataBits,
                    parity = parity,
                    stopBits = stopBits,
                    responseTimeoutMs = timeout,
                    interRequestDelayMs = gap,
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
