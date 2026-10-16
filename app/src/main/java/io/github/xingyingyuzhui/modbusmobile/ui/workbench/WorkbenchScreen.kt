package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

@Composable
fun WorkbenchScreen() {
    val session = LocalSession.current
    val model: WorkbenchViewModel = viewModel { WorkbenchViewModel(session) }
    val sessionState by model.sessionState.collectAsStateWithLifecycle()
    val rows = model.rows
    val slaveRows = model.slaveRows
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("工作台", style = MaterialTheme.typography.headlineSmall)
        val running = sessionState as? SessionState.Running
        if (running == null) {
            Text("先在连接页进入主站或从站。")
            return@Column
        }
        Text(if (running.role == Role.MASTER) "主站 · 单次读写" else "从站 · Unit 1")
        if (running.role == Role.MASTER) {
            Text("读取区域")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Area.entries.forEach { area ->
                    FilterChip(
                        selected = model.area == area.area,
                        onClick = { model.area = area.area },
                        label = { Text(area.label) },
                    )
                }
            }
            NumberField("Unit", model.unit) { model.unit = it }
            NumberField("地址", model.address) { model.address = it }
            NumberField("数量", model.quantity) { model.quantity = it }
            Button(onClick = model::read, modifier = Modifier.fillMaxWidth()) { Text("读取") }
            Text("写单个保持寄存器")
            NumberField("写入值", model.writeValue) { model.writeValue = it }
            Button(onClick = model::writeHolding, modifier = Modifier.fillMaxWidth()) { Text("写入") }
            rows.forEach { line -> Text(line, style = Mono) }
        } else {
            Text("保持寄存器", style = MaterialTheme.typography.titleMedium)
            slaveRows.holding.forEach { cell ->
                NumberField("HR ${cell.address}", cell.draft) { model.editHolding(cell.address, it) }
            }
            Text("线圈", style = MaterialTheme.typography.titleMedium)
            slaveRows.coils.forEach { cell ->
                FilterChip(
                    selected = cell.value,
                    onClick = { model.editCoil(cell.address, !cell.value) },
                    label = { Text("Coil ${cell.address} = ${if (cell.value) "ON" else "OFF"}") },
                )
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private enum class Area(val area: RegisterArea, val label: String) {
    Coil(RegisterArea.COIL, "线圈"),
    Discrete(RegisterArea.DISCRETE_INPUT, "离散量"),
    Holding(RegisterArea.HOLDING_REGISTER, "保持寄存器"),
    Input(RegisterArea.INPUT_REGISTER, "输入寄存器"),
}
