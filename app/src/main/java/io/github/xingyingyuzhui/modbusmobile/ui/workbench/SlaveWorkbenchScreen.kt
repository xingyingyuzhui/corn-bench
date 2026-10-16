package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.AddressStyle
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.modiconLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChipRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.components.RegisterRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.RegisterTable
import io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.components.WarnIcon
import io.github.xingyingyuzhui.modbusmobile.ui.resolve
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalUiSettings

private val SlaveAreas = listOf(
    RegisterArea.HOLDING_REGISTER,
    RegisterArea.INPUT_REGISTER,
    RegisterArea.COIL,
    RegisterArea.DISCRETE_INPUT,
)

private fun RegisterArea.isBitArea() = this == RegisterArea.COIL || this == RegisterArea.DISCRETE_INPUT

@Composable
fun SlaveWorkbench(target: ConnectionTarget, landscape: Boolean) {
    val session = LocalSession.current
    val model = viewModel { SlaveViewModel(session) }
    val settings = LocalUiSettings.current
    if (landscape) {
        SlaveLandscape(model, settings, target)
    } else {
        SlavePortrait(model, settings, target)
    }
    SlaveDialogs(model)
}

@Composable
private fun SlavePortrait(model: SlaveViewModel, settings: UiSettings, target: ConnectionTarget) {
    Column(Modifier.screenFrame()) {
        PageHeader(stringResource(R.string.title_slave_workbench))
        SessionStrip(Modifier.padding(bottom = 8.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = dockClearance()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (target == ConnectionTarget.Demo) DemoNote()
            AreaSegment(model)
            AreaTable(model, settings, landscape = false)
            SecondaryButton(stringResource(R.string.slave_add_range), model::openAddRange, Modifier.fillMaxWidth(), compact = true)
        }
    }
}

@Composable
private fun SlaveLandscape(model: SlaveViewModel, settings: UiSettings, target: ConnectionTarget) {
    Row(
        Modifier.screenFrame().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SessionStrip()
            if (target == ConnectionTarget.Demo) DemoNote()
            AreaSegment(model)
            SecondaryButton(stringResource(R.string.slave_add_range), model::openAddRange, Modifier.fillMaxWidth(), compact = true)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            AreaTable(model, settings, landscape = true)
        }
    }
}

/** 演示设备的对端主站会每秒读 HR0–9、每 5 秒把 HR0 改写成递增值（DefaultSessionManager.runDemoMaster）。 */
@Composable
private fun DemoNote() {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        WarnIcon(colors.accentText, Modifier.padding(top = 2.dp))
        Text(stringResource(R.string.slave_demo_note), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun AreaSegment(model: SlaveViewModel) {
    SegmentedTextControl(
        options = SlaveAreas,
        selected = model.area,
        onSelect = model::selectArea,
        small = true,
    ) { area ->
        stringResource(R.string.slave_area_count, areaShortName(area), model.rowsOf(area).size)
    }
}

@Composable
private fun areaShortName(area: RegisterArea): String = stringResource(
    when (area) {
        RegisterArea.COIL -> R.string.area_short_coil
        RegisterArea.DISCRETE_INPUT -> R.string.area_short_discrete
        RegisterArea.HOLDING_REGISTER -> R.string.area_short_holding
        RegisterArea.INPUT_REGISTER -> R.string.area_short_input
    },
)

@Composable
private fun AreaTable(model: SlaveViewModel, settings: UiSettings, landscape: Boolean) {
    val colors = LocalModbusColors.current
    val area = model.area
    val rows = model.rowsOf(area)
    if (rows.isEmpty()) {
        val shape = RoundedCornerShape(CardRadius)
        Text(
            stringResource(R.string.slave_empty),
            color = colors.muted,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface1, shape)
                .border(1.dp, colors.line, shape)
                .padding(14.dp),
        )
        return
    }
    val on = stringResource(R.string.slave_bit_on)
    val off = stringResource(R.string.slave_bit_off)
    val bit = area.isBitArea()
    val table = rows.map { row ->
        RegisterRow(
            address = row.address,
            value = if (bit) (if (row.value != 0) on else off) else row.value.toString(),
            addressText = if (settings.addressStyle == AddressStyle.MODICON) modiconLabel(area, row.address) else null,
            sub = if (settings.addressStyle == AddressStyle.BOTH) modiconLabel(area, row.address) else null,
            hex = if (bit) null else hexWord(row.value, settings.hexPrefix),
            flashAt = row.changedAt,
            editable = true,
        )
    }
    RegisterTable(
        rows = table,
        showEdit = !landscape,
        rowHeight = if (landscape) 36.dp else 40.dp,
        addressWidth = if (landscape) 62.dp else 70.dp,
        hexWidth = if (landscape) 70.dp else 84.dp,
        onRowClick = { row ->
            if (bit) model.toggleBit(area, row.address) else model.startEdit(area, row.address)
        },
    )
}

@Composable
private fun SlaveDialogs(model: SlaveViewModel) {
    val colors = LocalModbusColors.current
    model.edit?.let { edit ->
        AlertDialog(
            onDismissRequest = model::cancelEdit,
            containerColor = colors.surface1,
            titleContentColor = colors.text,
            textContentColor = colors.muted,
            title = {
                Text(
                    stringResource(R.string.slave_edit_title, areaShortName(edit.area), edit.address),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            },
            text = {
                NumberInput(
                    stringResource(R.string.slave_edit_value),
                    edit.draft,
                    error = edit.error?.resolve(),
                    keyboardType = KeyboardType.Number,
                    onValue = model::setDraft,
                )
            },
            confirmButton = {
                TextButton(onClick = model::commitEdit) { Text(stringResource(R.string.slave_apply), color = colors.accentText) }
            },
            dismissButton = {
                TextButton(onClick = model::cancelEdit) { Text(stringResource(R.string.action_cancel), color = colors.muted) }
            },
        )
    }
    model.addRange?.let { request ->
        AlertDialog(
            onDismissRequest = model::cancelAddRange,
            containerColor = colors.surface1,
            titleContentColor = colors.text,
            textContentColor = colors.muted,
            title = { Text(stringResource(R.string.slave_add_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipRow(
                        SlaveAreas,
                        { it == request.area },
                        { areaShortName(it) },
                        { next -> model.updateAddRange { copy(area = next) } },
                        design = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberInput(
                            stringResource(R.string.slave_add_start),
                            request.start,
                            Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                            onValue = { model.updateAddRange { copy(start = it) } },
                        )
                        NumberInput(
                            stringResource(R.string.slave_add_count),
                            request.count,
                            Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                            onValue = { model.updateAddRange { copy(count = it) } },
                        )
                    }
                    request.error?.let { Text(it.resolve(), color = colors.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = model::commitAddRange) { Text(stringResource(R.string.slave_add_confirm), color = colors.accentText) }
            },
            dismissButton = {
                TextButton(onClick = model::cancelAddRange) { Text(stringResource(R.string.action_cancel), color = colors.muted) }
            },
        )
    }
}
