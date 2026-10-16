package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.AddressStyle
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.modiconLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChipRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.RegisterTable
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.resolve
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalUiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

private enum class Area(val area: RegisterArea, val label: Int) {
    Coil(RegisterArea.COIL, R.string.area_coil),
    Discrete(RegisterArea.DISCRETE_INPUT, R.string.area_discrete),
    Holding(RegisterArea.HOLDING_REGISTER, R.string.area_holding),
    Input(RegisterArea.INPUT_REGISTER, R.string.area_input),
}

@Composable
fun WorkbenchScreen() {
    val session = LocalSession.current
    val model = viewModel { WorkbenchViewModel(session) }
    val sessionState by model.sessionState.collectAsStateWithLifecycle()
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    val running = sessionState as? SessionState.Running
    Column(Modifier.screenFrame().padding(top = spacing.gapS), verticalArrangement = Arrangement.spacedBy(spacing.gapS)) {
        Text(stringResource(R.string.title_workbench), color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        SessionStrip()
        if (running == null) {
            Text(stringResource(R.string.workbench_need_session), color = colors.muted)
            return@Column
        }
        Text(
            stringResource(if (running.role == Role.MASTER) R.string.workbench_master else R.string.workbench_slave),
            color = colors.muted,
            fontSize = 13.sp,
        )
        if (landscape) {
            Row(Modifier.weight(1f).fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.gapM)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(spacing.gapS)) {
                    if (running.role == Role.MASTER) MasterForm(model) else SlaveForm(model)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(spacing.gapS)) {
                    if (running.role == Role.MASTER) MasterResult(model) else SlaveResult(model)
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(spacing.gapS)) {
                if (running.role == Role.MASTER) {
                    MasterForm(model)
                    MasterResult(model)
                } else {
                    SlaveForm(model)
                    SlaveResult(model)
                }
            }
        }
    }
}

@Composable
private fun MasterForm(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    val settings = LocalUiSettings.current
    ChipRow(
        Area.entries,
        { it.area == model.area },
        { stringResource(it.label) },
        { model.area = it.area },
        scroll = true,
    )
    NumberInput(stringResource(R.string.field_unit), model.unit) { model.unit = it }
    NumberInput(stringResource(R.string.field_address), model.address) { model.address = it }
    if (settings.addressStyle == AddressStyle.MODICON) {
        val address = model.address.toIntOrNull()
        if (address != null) {
            Text(modiconLabel(model.area, address), color = colors.dim, fontSize = 12.sp, style = Mono)
        }
    }
    NumberInput(stringResource(R.string.field_quantity), model.quantity) { model.quantity = it }
    PrimaryButton(stringResource(R.string.action_read), model::read)
    Text(stringResource(R.string.write_single_holding), color = colors.muted, fontSize = 13.sp)
    NumberInput(stringResource(R.string.field_write_value), model.writeValue) { model.writeValue = it }
    PrimaryButton(stringResource(R.string.action_write), model::writeHolding)
}

@Composable
private fun MasterResult(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    val settings = LocalUiSettings.current
    val rows = if (settings.addressStyle == AddressStyle.MODICON) {
        model.table.map { row -> row.copy(sub = modiconLabel(model.area, row.address)) }
    } else {
        model.table
    }
    if (rows.isNotEmpty()) RegisterTable(rows)
    val message = model.message
    if (message != null) {
        Text(message.resolve(), style = Mono, color = colors.text)
    } else if (rows.isEmpty()) {
        Text(stringResource(R.string.workbench_no_result), color = colors.muted)
    }
}

@Composable
private fun SlaveForm(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    Text(stringResource(R.string.slave_holding), color = colors.text, fontWeight = FontWeight.SemiBold)
    model.slaveRows.holding.forEach { cell ->
        NumberInput(stringResource(R.string.area_holding) + " ${cell.address}", cell.draft) {
            model.editHolding(cell.address, it)
        }
    }
}

@Composable
private fun SlaveResult(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    Text(stringResource(R.string.slave_coils), color = colors.text, fontWeight = FontWeight.SemiBold)
    ChipRow(
        model.slaveRows.coils,
        { it.value },
        { coil ->
            stringResource(R.string.coil_state, coil.address, stringResource(if (coil.value) R.string.coil_on else R.string.coil_off))
        },
        { coil -> model.editCoil(coil.address, !coil.value) },
    )
}
