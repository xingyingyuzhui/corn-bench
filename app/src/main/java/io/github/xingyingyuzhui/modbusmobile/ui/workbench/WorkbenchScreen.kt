package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.AddressStyle
import io.github.xingyingyuzhui.modbusmobile.application.NumberFormat
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.modiconLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChipRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChoiceTile
import io.github.xingyingyuzhui.modbusmobile.ui.components.ConfirmSheet
import io.github.xingyingyuzhui.modbusmobile.ui.components.GearIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.HeaderIconButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.HexFrame
import io.github.xingyingyuzhui.modbusmobile.ui.components.MoreIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.RefreshIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.RegisterRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.RegisterTable
import io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticBadge
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticKind
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.resolve
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalUiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ReadAreas = listOf(
    RegisterArea.COIL,
    RegisterArea.DISCRETE_INPUT,
    RegisterArea.HOLDING_REGISTER,
    RegisterArea.INPUT_REGISTER,
)

private val WriteAreas = listOf(RegisterArea.COIL, RegisterArea.HOLDING_REGISTER)

@Composable
fun WorkbenchScreen() {
    val session = LocalSession.current
    val model = viewModel { WorkbenchViewModel(session) }
    val sessionState by model.sessionState.collectAsStateWithLifecycle()
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    val running = sessionState as? SessionState.Running
    if (running?.role == Role.MASTER) {
        MasterWorkbench(model, landscape)
    } else {
        // 没有会话 / 从站：本轮不改（从站工作台属于后续 PR）。
        LegacyWorkbench(model, running, landscape)
    }
}

// ---------------------------------------------------------------------------------------------
// 主站工作台
// ---------------------------------------------------------------------------------------------

@Composable
private fun MasterWorkbench(model: WorkbenchViewModel, landscape: Boolean) {
    val settings = LocalUiSettings.current
    val format = model.formatOverride ?: settings.numberFormat
    if (landscape) {
        MasterLandscape(model, settings, format)
    } else {
        MasterPortrait(model, settings, format)
    }
    model.pending?.let { WriteConfirm(it, model, settings) }
}

@Composable
private fun MasterPortrait(model: WorkbenchViewModel, settings: UiSettings, format: NumberFormat) {
    val colors = LocalModbusColors.current
    val scroll = rememberScrollState()
    Column(Modifier.screenFrame()) {
        PageHeader(stringResource(R.string.title_workbench)) { WorkbenchMenu(model) }
        SessionStrip(Modifier.padding(bottom = 8.dp))
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(bottom = dockClearance()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RequestCard(model, settings, landscape = false)
            model.formError?.let { Text(it.resolve(), color = colors.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 2.dp)) }
            if (model.mode == WorkbenchMode.READ) {
                ReadSection(model, settings, format, landscape = false)
            } else {
                RecentSection(model)
            }
        }
    }
}

@Composable
private fun MasterLandscape(model: WorkbenchViewModel, settings: UiSettings, format: NumberFormat) {
    val colors = LocalModbusColors.current
    val leftScroll = rememberScrollState()
    val rightScroll = rememberScrollState()
    Row(
        Modifier.screenFrame().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.width(280.dp).fillMaxHeight().verticalScroll(leftScroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SessionStrip()
            RequestCard(model, settings, landscape = true)
            model.formError?.let { Text(it.resolve(), color = colors.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 2.dp)) }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rightScroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (model.mode == WorkbenchMode.READ) {
                ReadSection(model, settings, format, landscape = true)
            } else {
                RecentSection(model)
            }
        }
    }
}

@Composable
private fun WorkbenchMenu(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderIconButton(stringResource(R.string.menu_more), { open = true }) { MoreIcon(colors.muted) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PollIntervalsMs.forEach { ms ->
                val on = ms == model.pollIntervalMs
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.menu_poll_interval, secondsLabel(ms)),
                            color = if (on) colors.accentText else colors.text,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        model.setPollInterval(ms)
                        open = false
                    },
                )
            }
            HorizontalDivider(color = colors.line)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_clear_result), color = colors.text) },
                onClick = {
                    model.clearResult()
                    open = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_clear_recent), color = colors.text) },
                onClick = {
                    model.clearRecentWrites()
                    open = false
                },
            )
        }
    }
}

@Composable
private fun areaShortLabel(area: RegisterArea): String = stringResource(
    when (area) {
        RegisterArea.COIL -> R.string.area_short_coil
        RegisterArea.DISCRETE_INPUT -> R.string.area_short_discrete
        RegisterArea.HOLDING_REGISTER -> R.string.area_short_holding
        RegisterArea.INPUT_REGISTER -> R.string.area_short_input
    },
)

private fun readFunctionCode(area: RegisterArea): Int = when (area) {
    RegisterArea.COIL -> 0x01
    RegisterArea.DISCRETE_INPUT -> 0x02
    RegisterArea.HOLDING_REGISTER -> 0x03
    RegisterArea.INPUT_REGISTER -> 0x04
}

private fun referenceLabel(settings: UiSettings, area: RegisterArea, addressText: String): String? {
    if (settings.addressStyle != AddressStyle.MODICON) return null
    val address = addressText.trim().toIntOrNull() ?: return null
    if (address !in 0..0xFFFF) return null
    return modiconLabel(area, address)
}

@Composable
private fun RequestCard(model: WorkbenchViewModel, settings: UiSettings, landscape: Boolean) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    val tileMin = if (landscape) 44.dp else 50.dp
    val writing = model.mode == WorkbenchMode.WRITE
    val activeArea = if (writing) model.writeArea else model.area
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape)
            .padding(if (landscape) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 10.dp),
    ) {
        SegmentedTextControl(
            WorkbenchMode.entries,
            model.mode,
            model::selectMode,
            modifier = if (landscape) Modifier else Modifier.width(150.dp),
            small = true,
        ) { stringResource(if (it == WorkbenchMode.READ) R.string.action_read else R.string.action_write) }

        if (writing) {
            SegmentedTextControl(WriteAreas, model.writeArea, model::selectWriteArea, small = landscape) {
                stringResource(if (it == RegisterArea.COIL) R.string.area_coil else R.string.area_holding)
            }
        } else {
            SegmentedTextControl(ReadAreas, model.area, { model.area = it }, small = landscape) { areaShortLabel(it) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberInput(
                stringResource(R.string.field_unit),
                model.unit,
                Modifier.weight(if (landscape) 0.7f else 0.8f),
                minHeight = tileMin,
            ) { model.unit = it }
            NumberInput(
                stringResource(if (landscape) R.string.field_address else R.string.field_start_address),
                model.address,
                Modifier.weight(1.3f),
                sub = referenceLabel(settings, activeArea, model.address),
                minHeight = tileMin,
            ) { model.address = it }
            if (!writing) {
                NumberInput(
                    stringResource(R.string.field_quantity),
                    model.quantity,
                    Modifier.weight(1f),
                    minHeight = tileMin,
                ) { model.quantity = it }
            } else if (model.writeArea == RegisterArea.HOLDING_REGISTER) {
                ChoiceTile(
                    stringResource(R.string.field_format),
                    stringResource(if (model.writeFormat == WriteFormat.DEC) R.string.format_dec else R.string.format_hex),
                    model::toggleWriteFormat,
                    Modifier.weight(1f),
                    minHeight = tileMin,
                )
            }
        }

        if (writing) WriteValues(model, landscape) else ReadButtons(model)
    }
}

@Composable
private fun ReadButtons(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(
            stringResource(R.string.action_read_fc, fcLabel(readFunctionCode(model.area))),
            model::read,
            Modifier.weight(1.4f),
            compact = true,
        )
        val seconds = secondsLabel(model.pollIntervalMs)
        SecondaryButton(
            stringResource(if (model.polling) R.string.poll_running else R.string.poll_idle, seconds),
            { model.changePolling(!model.polling) },
            Modifier.weight(1f),
            compact = true,
            active = model.polling,
            icon = { RefreshIcon(if (model.polling) colors.accentText else colors.muted) },
        )
    }
}

@Composable
private fun WriteValues(model: WorkbenchViewModel, landscape: Boolean) {
    val colors = LocalModbusColors.current
    val coil = model.writeArea == RegisterArea.COIL
    val count = if (coil) model.writeCoils.size else model.writeWords.size
    val multi = count > 1 || model.forceMultiple
    val fc = when {
        coil && multi -> 0x0F
        coil -> 0x05
        multi -> 0x10
        else -> 0x06
    }
    val label = when {
        count == 1 && !model.forceMultiple -> stringResource(R.string.write_values_single, fcLabel(fc))
        count == 1 -> stringResource(R.string.write_values_forced, count, fcLabel(fc))
        else -> stringResource(R.string.write_values_auto, count, fcLabel(fc))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (count > 1) {
            Text(
                stringResource(R.string.write_remove),
                color = colors.muted,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = model::removeValue).padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            )
        }
        Text(
            stringResource(R.string.write_add),
            color = colors.txText,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = model::addValue).padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        )
    }
    val start = model.address.trim().toIntOrNull()
    val tileMin = if (landscape) 44.dp else 50.dp
    val indices = (0 until count).toList()
    indices.chunked(3).forEach { chunk ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chunk.forEach { index ->
                val cellLabel = start?.let { (it + index).toString() } ?: "—"
                if (coil) {
                    val on = model.writeCoils[index]
                    ChoiceTile(
                        cellLabel,
                        if (on) "ON" else "OFF",
                        { model.toggleCoil(index) },
                        Modifier.weight(1f),
                        minHeight = tileMin,
                        mono = true,
                        valueColor = if (on) colors.txText else colors.muted,
                    )
                } else {
                    NumberInput(
                        cellLabel,
                        model.writeWords[index],
                        Modifier.weight(1f),
                        minHeight = tileMin,
                        keyboardType = if (model.writeFormat == WriteFormat.HEX) KeyboardType.Ascii else KeyboardType.Number,
                    ) { model.setWordText(index, it) }
                }
            }
            repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    val planned = model.currentPlan()
    if (planned is PlanResult.Ok) HexFrame(planned.plan.frame, highlight = planned.plan.payload, compact = true)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(
            stringResource(R.string.write_preview, fcLabel(fc)),
            model::requestWrite,
            Modifier.weight(1f),
            compact = true,
        )
        SecondaryButton(
            stringResource(R.string.write_force, if (coil) "0F" else "16"),
            model::toggleForceMultiple,
            Modifier.width(92.dp),
            compact = true,
            fontSize = 12.sp,
            active = model.forceMultiple,
            icon = { GearIcon(if (model.forceMultiple) colors.accentText else colors.muted) },
        )
    }
}

// ---- 读取结果 ----

@Composable
private fun ReadSection(model: WorkbenchViewModel, settings: UiSettings, format: NumberFormat, landscape: Boolean) {
    val colors = LocalModbusColors.current
    when (val state = model.readState) {
        ReadState.None -> Text(stringResource(R.string.workbench_no_result), color = colors.muted, modifier = Modifier.padding(horizontal = 2.dp))
        is ReadState.Failed -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SemanticBadge(SemanticKind.ERROR, state.badge.resolve())
            Text(state.message.resolve(), color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
        is ReadState.Ok -> {
            val result = state.result
            if (!landscape) {
                ResultHead(model, result, format)
            }
            ResultTable(model, result, settings, format, landscape)
        }
    }
}

@Composable
private fun ResultHead(model: WorkbenchViewModel, result: ReadResult, format: NumberFormat) {
    val colors = LocalModbusColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SemanticBadge(SemanticKind.RX_LOCAL, stringResource(R.string.result_ok))
            Text(
                stringResource(R.string.result_stats, result.elapsedMs, result.values.size),
                style = Mono.copy(fontSize = 12.sp),
                color = colors.muted,
                maxLines = 1,
            )
        }
        if (!result.isBits) {
            ChipRow(NumberFormat.entries, { it == format }, { it.name }, { model.formatOverride = it }, xs = true)
        }
    }
}

@Composable
private fun ResultTable(
    model: WorkbenchViewModel,
    result: ReadResult,
    settings: UiSettings,
    format: NumberFormat,
    landscape: Boolean,
) {
    val on = stringResource(R.string.coil_on)
    val off = stringResource(R.string.coil_off)
    val formatted = if (result.isBits) emptyList() else formatRegisterValues(result.values, format, settings.hexPrefix)
    val editable = result.area == RegisterArea.COIL || result.area == RegisterArea.HOLDING_REGISTER
    val rows = result.values.mapIndexed { index, value ->
        val address = result.start + index
        RegisterRow(
            address = address,
            value = if (result.isBits) (if (value != 0) on else off) else formatted[index],
            sub = if (settings.addressStyle == AddressStyle.MODICON) modiconLabel(result.area, address) else null,
            hex = if (result.isBits) null else hexWord(value, settings.hexPrefix),
            flashAt = result.changedAt[address],
            editable = editable,
        )
    }
    val header = if (landscape && !result.isBits) {
        stringResource(R.string.table_value_format, format.name)
    } else {
        stringResource(R.string.table_value)
    }
    val cycle = NumberFormat.entries[(format.ordinal + 1) % NumberFormat.entries.size]
    RegisterTable(
        rows,
        valueHeader = header,
        showEdit = !landscape,
        rowHeight = if (landscape) 36.dp else 40.dp,
        addressWidth = if (landscape) 62.dp else 70.dp,
        hexWidth = if (landscape) 70.dp else 84.dp,
        onHeaderValueClick = if (landscape && !result.isBits) ({ model.formatOverride = cycle }) else null,
        onRowClick = { row -> model.editRow(result, row.address) },
    )
}

// ---- 最近写入 ----

private val ClockFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss", Locale.ROOT) }

@Composable
private fun RecentSection(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.recent_title), color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.recent_clear),
            color = colors.muted,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = model::clearRecentWrites).padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape),
    ) {
        if (model.recentWrites.isEmpty()) {
            Text(
                stringResource(R.string.recent_empty),
                color = colors.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            )
        }
        model.recentWrites.forEachIndexed { index, entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clickable { model.reuse(entry) }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val kind = when (entry.outcome) {
                    WriteOutcome.OK -> SemanticKind.RX_LOCAL
                    WriteOutcome.UNKNOWN -> SemanticKind.AMBIGUOUS
                    else -> SemanticKind.ERROR
                }
                SemanticBadge(kind, outcomeLabel(entry))
                Text(writeSummary(entry), style = Mono, color = colors.text, maxLines = 1, modifier = Modifier.weight(1f))
                Text(
                    ClockFormat.get()!!.format(Date(entry.atMillis)),
                    style = Mono,
                    color = colors.muted,
                    maxLines = 1,
                )
            }
            if (index != model.recentWrites.lastIndex) HorizontalDivider(color = colors.rowLine)
        }
    }
}

@Composable
private fun outcomeLabel(entry: RecentWrite): String = when (entry.outcome) {
    WriteOutcome.OK -> stringResource(R.string.result_ok)
    WriteOutcome.EXCEPTION -> stringResource(R.string.badge_exception, entry.exceptionCode ?: 0)
    WriteOutcome.TIMEOUT -> stringResource(R.string.badge_timeout)
    WriteOutcome.UNKNOWN -> stringResource(R.string.badge_unknown)
    WriteOutcome.DISCONNECTED -> stringResource(R.string.badge_disconnected)
    WriteOutcome.INVALID -> stringResource(R.string.badge_invalid)
}

/** “FC06 U1 5 = 123”；多个值：“FC10 U1 100 × 3”。 */
private fun writeSummary(entry: RecentWrite): String {
    val head = "${fcLabel(entry.functionCode)} U${entry.unit} ${entry.address}"
    val count = entry.words.size + entry.bits.size
    return when {
        count != 1 -> "$head × $count"
        entry.area == RegisterArea.COIL -> "$head = ${if (entry.bits[0]) "ON" else "OFF"}"
        else -> "$head = ${entry.words[0]}"
    }
}

// ---- 写入确认 ----

@Composable
private fun WriteConfirm(pending: PendingWrite, model: WorkbenchViewModel, settings: UiSettings) {
    val plan = pending.plan
    val coil = plan.area == RegisterArea.COIL
    val multi = plan.words.size + plan.bits.size > 1
    val haptic = LocalHapticFeedback.current
    ConfirmSheet(
        title = stringResource(
            when {
                coil && multi -> R.string.confirm_title_coils
                coil -> R.string.confirm_title_coil
                multi -> R.string.confirm_title_registers
                else -> R.string.confirm_title_register
            },
        ),
        confirmLabel = stringResource(R.string.action_confirm),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = {
            if (settings.vibration) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            model.confirmWrite()
        },
        onDismiss = model::dismissPending,
        badge = stringResource(R.string.confirm_badge, plan.unit, plan.address, modiconLabel(plan.area, plan.address)),
        frame = plan.frame,
        frameHighlight = plan.payload,
        frameCaption = stringResource(R.string.confirm_frame_caption, plan.frame.size, fcLabel(plan.functionCode)),
        warning = stringResource(if (coil) R.string.confirm_warning_coil else R.string.confirm_warning),
    ) {
        ConfirmValues(pending, model)
    }
}

@Composable
private fun ConfirmValues(pending: PendingWrite, model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    val plan = pending.plan
    val coil = plan.area == RegisterArea.COIL
    val unknown = stringResource(R.string.confirm_current_unknown)
    fun show(value: Int?, isCoil: Boolean): String = when {
        value == null -> "—"
        isCoil -> if (value != 0) "ON" else "OFF"
        else -> value.toString()
    }
    if (plan.words.size + plan.bits.size == 1) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(stringResource(R.string.confirm_current), color = colors.muted, fontSize = 11.sp)
                Text(show(pending.current.firstOrNull(), coil), style = Mono.copy(fontSize = 22.sp), color = colors.muted)
                if (pending.current.firstOrNull() == null) Text(unknown, color = colors.dim, fontSize = 11.sp)
            }
            Text("→", color = colors.dim, fontSize = 20.sp, modifier = Modifier.padding(bottom = 4.dp))
            if (coil) {
                SegmentedTextControl(
                    listOf(false, true),
                    plan.bits[0],
                    model::setPendingCoil,
                    Modifier.weight(1f),
                ) { if (it) "ON" else "OFF" }
            } else {
                Column {
                    Text(stringResource(R.string.confirm_new), color = colors.muted, fontSize = 11.sp)
                    Text(
                        plan.words[0].toString(),
                        style = Mono.copy(fontSize = 22.sp),
                        color = colors.txText,
                    )
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val total = plan.words.size + plan.bits.size
            Text(stringResource(R.string.confirm_multi_head, plan.address, total), color = colors.muted, fontSize = 12.sp)
            val shown = minOf(total, 6)
            repeat(shown) { index ->
                val new = if (coil) (if (plan.bits[index]) 1 else 0) else plan.words[index]
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text((plan.address + index).toString(), style = Mono, color = colors.dim, modifier = Modifier.width(56.dp))
                    Text(show(pending.current.getOrNull(index), coil), style = Mono, color = colors.muted)
                    Text("→", color = colors.dim, fontSize = 13.sp)
                    Text(show(new, coil), style = Mono, color = colors.txText)
                }
            }
            if (total > shown) Text(stringResource(R.string.confirm_multi_more, total), color = colors.dim, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 没有会话 / 从站：保持原样
// ---------------------------------------------------------------------------------------------

@Composable
private fun LegacyWorkbench(model: WorkbenchViewModel, running: SessionState.Running?, landscape: Boolean) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val formScroll = rememberScrollState()
    val resultScroll = rememberScrollState()
    Column(Modifier.screenFrame().padding(top = spacing.gapS), verticalArrangement = Arrangement.spacedBy(spacing.gapS)) {
        Text(stringResource(R.string.title_workbench), color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        SessionStrip()
        if (running == null) {
            Text(stringResource(R.string.workbench_need_session), color = colors.muted)
            return@Column
        }
        Text(stringResource(R.string.workbench_slave), color = colors.muted, fontSize = 13.sp)
        if (landscape) {
            Row(Modifier.weight(1f).fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.gapM)) {
                Column(
                    Modifier.weight(1f).verticalScroll(formScroll).padding(bottom = dockClearance()),
                    verticalArrangement = Arrangement.spacedBy(spacing.gapS),
                ) { SlaveForm(model) }
                Column(
                    Modifier.weight(1f).verticalScroll(resultScroll).padding(bottom = dockClearance()),
                    verticalArrangement = Arrangement.spacedBy(spacing.gapS),
                ) { SlaveResult(model) }
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(formScroll).padding(bottom = dockClearance()),
                verticalArrangement = Arrangement.spacedBy(spacing.gapS),
            ) {
                SlaveForm(model)
                SlaveResult(model)
            }
        }
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
