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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xingyingyuzhui.modbusmobile.R
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

@Composable
fun SessionStrip(modifier: Modifier = Modifier) {
    val session = LocalSession.current
    val state by session.state.collectAsStateWithLifecycle()
    val serial by session.serial.collectAsStateWithLifecycle()
    val devices by session.usbDevices.collectAsStateWithLifecycle()
    val open = LocalOpenConnection.current
    val deviceName = (state as? SessionState.Running)?.let { running ->
        when (val target = running.target) {
            ConnectionTarget.Demo -> stringResource(R.string.device_demo)
            is ConnectionTarget.Usb -> devices.firstOrNull {
                it.deviceId == target.deviceId && it.portIndex == target.portIndex
            }?.productName ?: stringResource(R.string.device_usb)
        }
    }
    SessionStripContent(state, serial, deviceName, open, modifier)
}

@Composable
fun SessionStripContent(
    state: SessionState,
    serial: SerialSettings?,
    deviceName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
    val detail = when {
        deviceName != null && serial != null -> "$deviceName · ${serial.wireLabel()}"
        serial != null -> serial.wireLabel()
        else -> ""
    }
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
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Column {
            Text(title, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (detail.isNotEmpty()) {
                Text(detail, style = Mono.copy(fontSize = 12.sp), color = colors.muted)
            }
        }
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
