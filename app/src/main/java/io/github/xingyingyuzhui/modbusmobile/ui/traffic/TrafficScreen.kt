package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Amber
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Ok

@Composable
fun TrafficScreen() {
    val session = LocalSession.current
    val model: TrafficViewModel = viewModel { TrafficViewModel(session) }
    val events by model.events.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("通信日志", style = MaterialTheme.typography.headlineSmall)
        Text("${events.size} / 1000", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (events.isEmpty()) {
            Text("还没有报文。")
        } else {
            val origin = events.first().monotonicNs
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(events.asReversed()) { event ->
                    val deltaMs = (event.monotonicNs - origin) / 1_000_000
                    Text(
                        "%+8d ms  %s  %s".format(deltaMs, event.direction.name, event.hex),
                        style = Mono,
                        color = if (event.direction == TrafficDirection.TX) Amber else Ok,
                    )
                }
            }
        }
    }
}
