package io.github.xingyingyuzhui.modbusmobile.ui.curve

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChipRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.SmallButton
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing

@Composable
fun CurveFullscreenScreen(onClose: () -> Unit) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    var window by rememberSaveable { mutableIntStateOf(1) }
    val options = listOf(
        0 to R.string.curve_1m,
        1 to R.string.curve_5m,
        2 to R.string.curve_15m,
        3 to R.string.curve_1h,
    )
    Column(Modifier.screenFrame().padding(top = spacing.gapS), verticalArrangement = Arrangement.spacedBy(spacing.gapM)) {
        Text(stringResource(R.string.title_curve), color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.curve_pending), color = colors.muted, fontSize = 14.sp)
        ChipRow(
            options,
            { it.first == window },
            { stringResource(it.second) },
            { window = it.first },
            scroll = true,
        )
        SmallButton(stringResource(R.string.action_close), onClose)
    }
}
