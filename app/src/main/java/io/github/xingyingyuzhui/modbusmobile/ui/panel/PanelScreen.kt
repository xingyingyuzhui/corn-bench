package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticBadge
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticKind
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.pageStart
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

private data class Sample(val name: Int, val value: Int, val unit: Int?)

private val samples = listOf(
    Sample(R.string.sample_temp, R.string.sample_temp_value, R.string.sample_temp_unit),
    Sample(R.string.sample_pressure, R.string.sample_pressure_value, R.string.sample_pressure_unit),
    Sample(R.string.sample_compressor, R.string.sample_compressor_value, null),
    Sample(R.string.sample_valve, R.string.sample_valve_value, null),
)

@Composable
fun PanelScreen() {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val dock = LocalDockInsets.current
    val columns = if (LocalShellMode.current == ShellMode.LANDSCAPE) 4 else 2
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = pageStart(), end = spacing.page, top = spacing.gapS)) {
            Text(stringResource(R.string.title_panel), color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            SessionStrip(Modifier.padding(top = spacing.gapS, bottom = spacing.gapS))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = pageStart(),
                end = spacing.page,
                bottom = dock.bottom + spacing.gapS,
            ),
            horizontalArrangement = Arrangement.spacedBy(spacing.gapS),
            verticalArrangement = Arrangement.spacedBy(spacing.gapS),
        ) {
            items(samples) { sample ->
                ModbusCard {
                    Text(stringResource(sample.name), color = colors.muted, fontSize = 13.sp)
                    Text(stringResource(sample.value), style = Mono.copy(fontSize = 26.sp), color = colors.text)
                    val unit = sample.unit
                    if (unit != null) Text(stringResource(unit), color = colors.dim, fontSize = 12.sp)
                    SemanticBadge(SemanticKind.DROPPED, stringResource(R.string.panel_sample))
                }
            }
        }
    }
}
