package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    // G#9：稿面数值/统计字 weight 600。由官方可变字体实例化出 wght=600（OFL，许可见 licenses/）。
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

val Mono = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
)
