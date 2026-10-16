package io.github.xingyingyuzhui.modbusmobile.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.Parity
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Fault
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Ok

@Composable
fun ConnectionScreen() {
    val session = LocalSession.current
    val model: ConnectionViewModel = viewModel { ConnectionViewModel(session) }
    val state by model.state.collectAsStateWithLifecycle()
    val devices by model.devices.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("连接", style = MaterialTheme.typography.headlineSmall)
        Text(state.caption(), color = if (state is SessionState.Error) Fault else Ok)
        Text("角色", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = model.role == Role.MASTER, onClick = { model.role = Role.MASTER }, label = { Text("主站") })
            FilterChip(selected = model.role == Role.SLAVE, onClick = { model.role = Role.SLAVE }, label = { Text("从站") })
        }
        Text("串口", style = MaterialTheme.typography.titleMedium)
        OptionRow("波特率", listOf(1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200), model.baud) { model.baud = it }
        OptionRow("数据位", listOf(7, 8), model.dataBits) { model.dataBits = it }
        OptionRow("停止位", listOf(1, 2), model.stopBits) { model.stopBits = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Parity.entries.forEach { parity ->
                FilterChip(
                    selected = model.parity == parity,
                    onClick = { model.parity = parity },
                    label = { Text(parity.name) },
                )
            }
        }
        Text("设备", style = MaterialTheme.typography.titleMedium)
        DeviceRow("演示设备", "不接硬件，内存总线", model.selected == ConnectionTarget.Demo) {
            model.selected = ConnectionTarget.Demo
        }
        devices.forEach { device ->
            val target = ConnectionTarget.Usb(device.deviceId, device.portIndex)
            DeviceRow(
                title = device.productName,
                subtitle = "VID %04X  PID %04X  端口 %d".format(device.vendorId, device.productId, device.portIndex),
                selected = model.selected == target,
            ) { model.selected = target }
        }
        if (state is SessionState.Running) {
            OutlinedButton(onClick = model::disconnect, modifier = Modifier.fillMaxWidth()) { Text("断开") }
        } else {
            Button(
                onClick = model::connect,
                enabled = state !is SessionState.RequestingPermission && state !is SessionState.Opening && state !is SessionState.Stopping,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("连接") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionRow(label: String, values: List<T>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(value.toString()) })
            }
        }
    }
}

@Composable
private fun DeviceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(if (selected) "● $title" else title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun SessionState.caption(): String = when (this) {
    SessionState.Idle -> "空闲"
    SessionState.RequestingPermission -> "正在请求 USB 权限"
    SessionState.Opening -> "正在打开端口"
    is SessionState.Running -> "运行中 · ${role.name} · epoch $epoch"
    SessionState.Stopping -> "正在停止"
    is SessionState.Error -> reason
}
