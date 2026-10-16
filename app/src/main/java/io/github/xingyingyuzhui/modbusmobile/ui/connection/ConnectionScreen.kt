package io.github.xingyingyuzhui.modbusmobile.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.BusTopology
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.EchoMode
import io.github.xingyingyuzhui.modbusmobile.application.Parity
import io.github.xingyingyuzhui.modbusmobile.application.SerialPreset
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.application.InputError
import io.github.xingyingyuzhui.modbusmobile.application.framingLabel
import io.github.xingyingyuzhui.modbusmobile.application.wireLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChipRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.FieldTile
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SectionLabel
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedControl
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

private val baudChoices = listOf(1_200, 2_400, 4_800, 9_600, 19_200, 38_400, 57_600, 115_200)

@Composable
fun ConnectionScreen() {
    val session = LocalSession.current
    val model = viewModel { ConnectionViewModel(session) }
    val state by model.state.collectAsStateWithLifecycle()
    val devices by model.devices.collectAsStateWithLifecycle()
    val echoDetected by model.echoDetected.collectAsStateWithLifecycle()
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val running = state is SessionState.Running
    val busy = state is SessionState.RequestingPermission ||
        state is SessionState.Opening ||
        state is SessionState.Stopping
    val roleName = stringResource(if (model.role == Role.MASTER) R.string.role_master else R.string.role_slave)
    val bottomInset = dockClearance().let { if (it > 0.dp) it else spacing.gapS }
    Column(Modifier.screenFrame().padding(top = spacing.gapS, bottom = bottomInset)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.title_connection),
                color = colors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        if (state !is SessionState.Idle) {
            Text(
                state.caption(),
                color = if (state is SessionState.Error) colors.error else colors.muted,
                fontSize = 13.sp,
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = spacing.gapS),
            verticalArrangement = Arrangement.spacedBy(spacing.gapM),
        ) {
            ModbusCard {
                SectionLabel(stringResource(R.string.section_device)) {
                    Text(
                        stringResource(R.string.action_refresh),
                        color = colors.accentText,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .heightIn(min = MinTouch)
                            .clickable(onClick = model::refresh)
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
                DeviceRow(
                    icon = R.drawable.ic_device_demo,
                    title = stringResource(R.string.device_demo),
                    subtitle = stringResource(R.string.device_demo_sub),
                    selected = model.selected == ConnectionTarget.Demo,
                    onClick = { model.selected = ConnectionTarget.Demo },
                )
                devices.forEach { device ->
                    val target = ConnectionTarget.Usb(device.deviceId, device.portIndex)
                    DeviceRow(
                        icon = R.drawable.ic_device_usb,
                        title = device.productName,
                        subtitle = device.subtitle(),
                        selected = model.selected == target,
                        onClick = { model.selected = target },
                    )
                }
            }
            ModbusCard {
                SectionLabel(stringResource(R.string.section_role))
                SegmentedControl(
                    options = listOf(Role.MASTER, Role.SLAVE),
                    selected = model.role,
                    onSelect = { model.role = it },
                ) { role ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(if (role == Role.MASTER) R.string.role_master else R.string.role_slave),
                            color = if (model.role == role) colors.text else colors.muted,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Text(
                            stringResource(if (role == Role.MASTER) R.string.role_master_hint else R.string.role_slave_hint),
                            color = colors.dim,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            ModbusCard {
                SectionLabel(stringResource(R.string.section_serial)) {
                    Text(framingLabel(model.dataBits, model.parity, model.stopBits), style = Mono, color = colors.muted)
                }
                val presets = buildList<SerialPreset?> {
                    addAll(SerialPreset.entries)
                    add(null)
                }
                ChipRow(
                    options = presets,
                    isSelected = { option -> if (option == null) model.preset == null else model.preset == option },
                    label = { option -> option?.wireLabel() ?: stringResource(R.string.preset_custom) },
                    onToggle = { option ->
                        if (option == null) model.selectCustom() else model.selectPreset(option)
                    },
                    scroll = true,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldTile(
                        stringResource(R.string.field_baud),
                        model.baud.toString(),
                        modifier = Modifier.weight(1.4f),
                    )
                    FieldTile(
                        stringResource(R.string.field_data_bits),
                        model.dataBits.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    FieldTile(
                        stringResource(R.string.field_parity),
                        parityName(model.parity),
                        mono = false,
                        modifier = Modifier.weight(1f),
                    )
                    FieldTile(
                        stringResource(R.string.field_stop_bits),
                        model.stopBits.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (model.preset == null) {
                    Text(stringResource(R.string.field_baud), color = colors.muted, fontSize = 12.sp)
                    ChipRow(baudChoices, { it == model.baud }, { it.toString() }, { model.baud = it; model.selectCustom() }, scroll = true)
                    Text(stringResource(R.string.field_data_bits), color = colors.muted, fontSize = 12.sp)
                    ChipRow(listOf(7, 8), { it == model.dataBits }, { it.toString() }, { model.dataBits = it; model.selectCustom() })
                    Text(stringResource(R.string.field_parity), color = colors.muted, fontSize = 12.sp)
                    ChipRow(
                        Parity.entries,
                        { it == model.parity },
                        { parityName(it) },
                        { model.parity = it; model.selectCustom() },
                    )
                    Text(stringResource(R.string.field_stop_bits), color = colors.muted, fontSize = 12.sp)
                    ChipRow(listOf(1, 2), { it == model.stopBits }, { it.toString() }, { model.stopBits = it; model.selectCustom() })
                }
            }
            ModbusCard {
                SectionLabel(stringResource(R.string.section_echo))
                ChipRow(
                    EchoMode.entries,
                    { it == model.echoMode },
                    { echoName(it) },
                    { model.echoMode = it },
                )
                if (running) {
                    Text(echoDetected.caption(), color = colors.muted, fontSize = 12.sp)
                }
            }
            ModbusCard {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable { model.advancedOpen = !model.advancedOpen },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.advanced_summary,
                            stringResource(
                                R.string.advanced_detail,
                                model.timeoutText.trim().toLongOrNull() ?: 0,
                                model.gapText.trim().toLongOrNull() ?: 0,
                                echoName(model.echoMode),
                            ),
                        ),
                        color = colors.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(if (model.advancedOpen) "⌄" else "›", color = colors.dim, fontSize = 18.sp)
                }
                if (model.advancedOpen) {
                    if (model.role == Role.SLAVE) {
                        Text(stringResource(R.string.section_topology), color = colors.muted, fontSize = 12.sp)
                        ChipRow(
                            BusTopology.entries,
                            { it == model.topology },
                            { topologyName(it) },
                            { model.topology = it },
                        )
                    }
                    NumberInput(
                        stringResource(R.string.field_timeout),
                        model.timeoutText,
                        error = inputErrorText(model.validation.timeoutError),
                    ) { model.timeoutText = it }
                    NumberInput(
                        stringResource(R.string.field_gap),
                        model.gapText,
                        error = inputErrorText(model.validation.gapError),
                    ) { model.gapText = it }
                } else if (!model.validation.ok) {
                    val error = inputErrorText(model.validation.timeoutError ?: model.validation.gapError)
                    if (error != null) Text(error, color = colors.error, fontSize = 12.sp)
                }
            }
        }
        if (running) {
            SecondaryButton(stringResource(R.string.action_disconnect), model::disconnect, enabled = !busy)
        } else {
            PrimaryButton(
                stringResource(R.string.connect_action, roleName, model.wireLabel),
                model::connect,
                enabled = !busy && model.validation.ok,
            )
        }
    }
}

@Composable
private fun DeviceRow(
    icon: Int,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clip(shape)
            .background(if (selected) colors.wash(colors.accent) else colors.surface2)
            .border(1.dp, if (selected) colors.accent else colors.line, shape)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (selected) colors.accent else colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, style = Mono.copy(fontSize = 12.sp), color = colors.muted)
        }
        Box(
            Modifier
                .size(20.dp)
                .border(2.dp, if (selected) colors.accent else colors.dim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colors.accent))
            }
        }
    }
}

@Composable
private fun UsbDeviceSummary.subtitle(): String =
    stringResource(R.string.device_usb_sub, vendorId, productId, portIndex)

@Composable
private fun inputErrorText(error: InputError?): String? = when (error) {
    null -> null
    InputError.TIMEOUT_RANGE -> stringResource(R.string.error_timeout_range)
    InputError.GAP_POSITIVE -> stringResource(R.string.error_gap_positive)
}

@Composable
private fun parityName(parity: Parity): String = stringResource(
    when (parity) {
        Parity.NONE -> R.string.parity_none
        Parity.ODD -> R.string.parity_odd
        Parity.EVEN -> R.string.parity_even
    },
)

@Composable
private fun echoName(mode: EchoMode): String = stringResource(
    when (mode) {
        EchoMode.OFF -> R.string.echo_off
        EchoMode.ON -> R.string.echo_on
        EchoMode.AUTO -> R.string.echo_auto
    },
)

@Composable
private fun topologyName(topology: BusTopology): String = stringResource(
    when (topology) {
        BusTopology.POINT_TO_POINT -> R.string.topology_ptp
        BusTopology.MULTIDROP -> R.string.topology_multi
    },
)

@Composable
private fun Boolean?.caption(): String = stringResource(
    when (this) {
        true -> R.string.echo_detected_on
        false -> R.string.echo_detected_off
        null -> R.string.echo_detected_pending
    },
)

@Composable
private fun SessionState.caption(): String = when (this) {
    SessionState.Idle -> stringResource(R.string.session_idle)
    SessionState.RequestingPermission -> stringResource(R.string.session_permission)
    SessionState.Opening -> stringResource(R.string.session_opening)
    is SessionState.Running -> if (role == Role.MASTER) {
        stringResource(R.string.session_master)
    } else {
        stringResource(R.string.session_slave)
    }
    SessionState.Stopping -> stringResource(R.string.session_stopping)
    is SessionState.Error -> reason
}
