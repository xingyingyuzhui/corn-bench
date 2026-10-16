package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.BusTopology
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.wireLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.LocalOpenConnection
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

/**
 * 稿面 .sess：高 56、圆点 10 + 4 外圈浅底光晕、标题 14/600、副行 11.5 mono。
 * [trailing] = 右侧文字区（日志的“1,000 条上限 / 已暂停滚动”等，由页面给）。
 * [showEcho] = 副行末尾加“回显 有 / 无”（从站页；回显还没判出来时不写）。
 * 从站副行写“总线拓扑 · 串口参数”，主站写“设备名 · 串口参数”。
 */
@Composable
fun SessionStrip(
    modifier: Modifier = Modifier,
    showEcho: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val session = LocalSession.current
    val state by session.state.collectAsStateWithLifecycle()
    val serial by session.serial.collectAsStateWithLifecycle()
    val devices by session.usbDevices.collectAsStateWithLifecycle()
    val echoDetected by session.echoDetected.collectAsStateWithLifecycle()
    val open = LocalOpenConnection.current
    val deviceName = (state as? SessionState.Running)?.let { running ->
        when (val target = running.target) {
            ConnectionTarget.Demo -> stringResource(R.string.device_demo)
            is ConnectionTarget.Usb -> devices.firstOrNull {
                it.deviceId == target.deviceId && it.portIndex == target.portIndex
            }?.productName ?: stringResource(R.string.device_usb)
        }
    }
    SessionStripContent(
        state, serial, deviceName, open, modifier,
        echoDetected = if (showEcho) echoDetected else null,
        trailing = trailing,
    )
}

@Composable
fun SessionStripContent(
    state: SessionState,
    serial: SerialSettings?,
    deviceName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    echoDetected: Boolean? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalModbusColors.current
    val title = when (state) {
        SessionState.Idle -> stringResource(R.string.session_idle)
        SessionState.RequestingPermission -> stringResource(R.string.session_permission)
        SessionState.Opening -> stringResource(R.string.session_opening)
        is SessionState.Running -> if (state.role == Role.MASTER) {
            stringResource(R.string.session_master)
        } else {
            stringResource(R.string.session_slave)
        }
        SessionState.Stopping -> stringResource(R.string.session_stopping)
        is SessionState.Error -> state.reason
    }
    val slave = state is SessionState.Running && state.role == Role.SLAVE
    val lead = if (slave && serial != null) {
        stringResource(
            if (serial.topology == BusTopology.MULTIDROP) R.string.topology_multi else R.string.topology_ptp,
        )
    } else {
        deviceName
    }
    val echoText = when (echoDetected) {
        true -> stringResource(R.string.strip_echo_yes)
        false -> stringResource(R.string.strip_echo_none)
        null -> null
    }
    val detail = listOfNotNull(lead, serial?.wireLabel(), echoText).joinToString(" · ")
    val dot = when (state) {
        is SessionState.Running -> colors.rxLocal
        is SessionState.Error -> colors.error
        else -> colors.dim
    }
    val back = stringResource(R.string.session_open_connection)
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.line, shape)
            .semantics { contentDescription = back }
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 光晕：外圈 4dp 同色浅底，画在圆点外面，不占布局。
        Box(
            Modifier
                .size(10.dp)
                .drawBehind { drawCircle(dot.copy(alpha = if (colors.isDark) 0.14f else 0.12f), radius = 9.dp.toPx()) }
                .clip(CircleShape)
                .background(dot),
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (detail.isNotEmpty()) {
                Text(detail, style = Mono.copy(fontSize = 11.5.sp), color = colors.muted, modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (trailing != null) trailing()
    }
}

@Preview(name = "深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StripDark() = StripPreview(true)

@Preview(name = "浅色", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun StripLight() = StripPreview(false)

@Composable
private fun StripPreview(dark: Boolean) {
    ModbusTheme(UiSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        SessionStripContent(
            state = SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1),
            serial = SerialSettings(),
            deviceName = "演示设备",
            onClick = {},
        )
    }
}
