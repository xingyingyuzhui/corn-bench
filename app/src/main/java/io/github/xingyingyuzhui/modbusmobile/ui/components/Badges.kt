package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme

enum class SemanticKind {
    TX,
    RX_LOCAL,
    ERROR,
    FOREIGN,
    ECHO,
    AMBIGUOUS,
    DROPPED,
}

fun TrafficKind.semanticKind(): SemanticKind = when (this) {
    TrafficKind.TX_REQUEST, TrafficKind.TX_RESPONSE -> SemanticKind.TX
    TrafficKind.RX_LOCAL_REQUEST, TrafficKind.RX_RESPONSE -> SemanticKind.RX_LOCAL
    TrafficKind.RX_FOREIGN_REQUEST, TrafficKind.RX_FOREIGN_RESPONSE -> SemanticKind.FOREIGN
    TrafficKind.ECHO -> SemanticKind.ECHO
    TrafficKind.AMBIGUOUS -> SemanticKind.AMBIGUOUS
    TrafficKind.DROPPED -> SemanticKind.DROPPED
}

@Composable
fun TrafficKind.label(): String = androidx.compose.ui.res.stringResource(
    when (this) {
        TrafficKind.TX_REQUEST -> R.string.traffic_tx_request
        TrafficKind.TX_RESPONSE -> R.string.traffic_tx_response
        TrafficKind.RX_LOCAL_REQUEST -> R.string.traffic_rx_local
        TrafficKind.RX_FOREIGN_REQUEST -> R.string.traffic_rx_foreign_request
        TrafficKind.RX_FOREIGN_RESPONSE -> R.string.traffic_rx_foreign_response
        TrafficKind.RX_RESPONSE -> R.string.traffic_rx_response
        TrafficKind.ECHO -> R.string.traffic_echo
        TrafficKind.AMBIGUOUS -> R.string.traffic_ambiguous
        TrafficKind.DROPPED -> R.string.traffic_dropped
    },
)

@Composable
fun SemanticBadge(kind: SemanticKind, text: String, modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    val tone = when (kind) {
        SemanticKind.TX -> colors.txText
        SemanticKind.RX_LOCAL -> colors.rxLocal
        SemanticKind.ERROR -> colors.error
        SemanticKind.FOREIGN -> colors.foreign
        SemanticKind.ECHO -> colors.echo
        SemanticKind.AMBIGUOUS -> colors.ambiguous
        SemanticKind.DROPPED -> colors.dropped
    }
    Badge(text, tone, colors.wash(tone), modifier)
}

enum class Quality { OK, STALE, TIMEOUT, UNKNOWN }

@Composable
fun QualityBadge(quality: Quality, modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    val (label, tone) = when (quality) {
        Quality.OK -> androidx.compose.ui.res.stringResource(R.string.quality_ok) to colors.rxLocal
        Quality.STALE -> androidx.compose.ui.res.stringResource(R.string.quality_stale) to colors.dropped
        Quality.TIMEOUT -> androidx.compose.ui.res.stringResource(R.string.quality_timeout) to colors.error
        Quality.UNKNOWN -> androidx.compose.ui.res.stringResource(R.string.quality_unknown) to colors.ambiguous
    }
    Badge(label, tone, colors.wash(tone), modifier)
}

@Composable
private fun Badge(text: String, tone: Color, wash: Color, modifier: Modifier) {
    Text(
        text,
        color = tone,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(wash, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Preview(name = "深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BadgesDark() = BadgePreview(dark = true)

@Preview(name = "浅色", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun BadgesLight() = BadgePreview(dark = false)

@Composable
private fun BadgePreview(dark: Boolean) {
    ModbusTheme(UiSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SemanticBadge(SemanticKind.TX, "主站请求")
                SemanticBadge(SemanticKind.RX_LOCAL, "响应")
                SemanticBadge(SemanticKind.ERROR, "超时")
                SemanticBadge(SemanticKind.FOREIGN, "外站")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QualityBadge(Quality.OK)
                QualityBadge(Quality.STALE)
                QualityBadge(Quality.TIMEOUT)
                QualityBadge(Quality.UNKNOWN)
            }
        }
    }
}
