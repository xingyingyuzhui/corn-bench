package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

data class RegisterRow(
    val address: Int,
    val value: String,
    val sub: String? = null,
)

@Composable
fun RegisterTable(
    rows: List<RegisterRow>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.table_address), color = colors.dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.table_value), color = colors.dim, fontSize = 11.sp)
        }
        HorizontalDivider(color = colors.line)
        rows.forEachIndexed { index, row ->
            Row(
                Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.address.toString(), style = Mono, color = colors.text)
                    if (row.sub != null) Text(row.sub, color = colors.dim, fontSize = 10.sp, style = Mono)
                }
                Text(row.value, style = Mono.copy(fontSize = 17.sp), color = colors.text)
            }
            if (index != rows.lastIndex) HorizontalDivider(color = colors.rowLine)
        }
    }
}

@Preview(name = "深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TableDark() = TablePreview(true)

@Preview(name = "浅色", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun TableLight() = TablePreview(false)

@Composable
private fun TablePreview(dark: Boolean) {
    ModbusTheme(UiSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        RegisterTable(
            listOf(
                RegisterRow(0, "0", "40001"),
                RegisterRow(1, "10", "40002"),
                RegisterRow(2, "20", "40003"),
            ),
        )
    }
}
