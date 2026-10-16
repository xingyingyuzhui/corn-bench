package io.github.xingyingyuzhui.modbusmobile.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.ui.theme.IslandRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors

@Composable
fun FloatingDock(
    current: String?,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(IslandRadius)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .fillMaxWidth(0.9f)
                .height(64.dp)
                .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
                .clip(shape)
                .background(colors.island)
                .border(1.dp, colors.line, shape)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { destination ->
                DockItem(
                    destination = destination,
                    selected = current == destination.route,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    iconSize = 22.dp,
                    labelSize = 10.5.sp,
                    radius = 18.dp,
                )
            }
        }
    }
}

@Composable
fun FloatingRail(
    current: String?,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(IslandRadius)
    Column(
        modifier
            .width(62.dp)
            .fillMaxHeight()
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
            .clip(shape)
            .background(colors.island)
            .border(1.dp, colors.line, shape)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Destination.entries.forEach { destination ->
            DockItem(
                destination = destination,
                selected = current == destination.route,
                onClick = { onSelect(destination) },
                modifier = Modifier.size(50.dp),
                iconSize = 20.dp,
                labelSize = 9.5.sp,
                radius = 16.dp,
            )
        }
    }
}

@Composable
private fun DockItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    iconSize: androidx.compose.ui.unit.Dp,
    labelSize: androidx.compose.ui.unit.TextUnit,
    radius: androidx.compose.ui.unit.Dp,
) {
    val colors = LocalModbusColors.current
    val label = stringResource(destination.label)
    val tint = if (selected) colors.accentText else colors.dim
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(if (selected) colors.wash(colors.accent) else Color.Transparent)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(destination.icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        Text(label, color = tint, fontSize = labelSize, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
