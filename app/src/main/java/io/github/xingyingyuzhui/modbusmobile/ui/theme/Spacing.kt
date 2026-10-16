package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ModbusSpacing(val scale: Float) {
    val page: Dp = (14f * scale).dp
    val gapS: Dp = (8f * scale).dp
    val gapM: Dp = (10f * scale).dp
}

val LocalSpacing = staticCompositionLocalOf { ModbusSpacing(1f) }

val CardRadius = 16.dp
val FieldRadius = 10.dp
val ChipRadius = 16.dp
val ButtonRadius = 14.dp
val IslandRadius = 24.dp
val MinTouch = 48.dp
