package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.HexFrameText
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticBadge
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.components.label
import io.github.xingyingyuzhui.modbusmobile.ui.components.semanticKind
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

@Composable
fun TrafficScreen() {
    val session = LocalSession.current
    val model = viewModel { TrafficViewModel(session) }
    val events by model.events.collectAsStateWithLifecycle()
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val effective = when {
        events.isEmpty() -> -1
        selected in events.indices -> selected
        else -> events.lastIndex
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.screenFrame().padding(top = spacing.gapS), verticalArrangement = Arrangement.spacedBy(spacing.gapS)) {
            Text(stringResource(R.string.title_log), color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            SessionStrip()
            Text(stringResource(R.string.traffic_count, events.size), color = colors.muted, fontSize = 12.sp)
            if (events.isEmpty()) {
                Text(stringResource(R.string.traffic_empty), color = colors.text)
            } else if (landscape) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing.gapM)) {
                    FrameList(
                        events = events,
                        selected = effective,
                        onSelect = { selected = it },
                        modifier = Modifier.weight(1f),
                    )
                    Column(Modifier.weight(1f).padding(bottom = spacing.gapM)) {
                        FrameDetail(events[effective])
                    }
                }
            } else {
                FrameList(
                    events = events,
                    selected = -1,
                    onSelect = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FrameList(
    events: List<TrafficEvent>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val origin = events.first().monotonicNs
    val reversed = events.asReversed()
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = dockClearance().takeIf { it > 0.dp } ?: spacing.gapS),
        verticalArrangement = Arrangement.spacedBy(spacing.gapS),
    ) {
        itemsIndexed(reversed) { index, event ->
            val original = events.lastIndex - index
            val delta = (event.monotonicNs - origin) / 1_000_000L
            val picked = original == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (picked) colors.wash(colors.accent) else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(original) }
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.traffic_delta, delta), style = Mono, color = colors.dim)
                    SemanticBadge(event.kind.semanticKind(), event.kind.label())
                }
                HexFrameText(event.bytes)
            }
        }
    }
}

@Composable
private fun FrameDetail(event: TrafficEvent) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.gapS)) {
        SemanticBadge(event.kind.semanticKind(), event.kind.label())
        HexFrameText(event.bytes)
    }
}
