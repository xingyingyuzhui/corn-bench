package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    frame: ByteArray? = null,
) {
    val colors = LocalModbusColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface1,
    ) {
        ConfirmSheetBody(title, body, confirmLabel, dismissLabel, onConfirm, onDismiss, frame)
    }
}

@Composable
fun ConfirmSheetBody(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    frame: ByteArray? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(body, color = colors.muted, fontSize = 14.sp)
        if (frame != null) HexFrameText(frame)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(dismissLabel, onDismiss, modifier = Modifier.weight(1f))
            PrimaryButton(confirmLabel, onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(name = "深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SheetDark() = SheetPreview(true)

@Preview(name = "浅色", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SheetLight() = SheetPreview(false)

@Composable
private fun SheetPreview(dark: Boolean) {
    ModbusTheme(UiSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        ConfirmSheetBody(
            title = stringResource(R.string.confirm_title),
            body = stringResource(R.string.confirm_body),
            confirmLabel = stringResource(R.string.action_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {},
            onDismiss = {},
            frame = byteArrayOf(0x01, 0x06, 0x00, 0x00, 0x00, 0x7B, 0xC8.toByte(), 0x3B),
        )
    }
}
