package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
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
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    val columns = if (landscape) 4 else 2
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = pageStart(), end = spacing.page)) {
            if (landscape) {
                // 稿面横屏：页头一行（面板 16/700），没有会话条，把高度还给卡片区（b4-3#1）。
                Text(
                    stringResource(R.string.title_panel),
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 2.dp),
                )
            } else {
                PageHeader(stringResource(R.string.title_panel))
                SessionStrip(Modifier.padding(bottom = spacing.gapS))
            }
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
                // 同一行卡片等高（稿面 164×145 / 164×140）：给统一最小高度。
                ModbusCard(
                    Modifier.heightIn(min = if (landscape) 130.dp else 108.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(stringResource(sample.name), color = colors.muted, fontSize = 12.5.sp)
                    // 稿面：值与单位同一行，单位 12sp。
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(sample.value), style = Mono.copy(fontSize = 26.sp), color = colors.text)
                        val unit = sample.unit
                        if (unit != null) {
                            Text(stringResource(unit), color = colors.dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    SemanticBadge(SemanticKind.DROPPED, stringResource(R.string.panel_sample))
                }
            }
        }
    }
}
