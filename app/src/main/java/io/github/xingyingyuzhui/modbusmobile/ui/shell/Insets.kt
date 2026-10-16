package io.github.xingyingyuzhui.modbusmobile.ui.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing

data class DockInsets(
    val start: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

enum class ShellMode { PORTRAIT, LANDSCAPE }

val LocalDockInsets = staticCompositionLocalOf { DockInsets() }

val LocalShellMode = staticCompositionLocalOf { ShellMode.PORTRAIT }

@Composable
fun pageStart(): Dp {
    val dock = LocalDockInsets.current
    val spacing = LocalSpacing.current
    return if (dock.start > 0.dp) dock.start else spacing.page
}

@Composable
fun Modifier.screenFrame(): Modifier {
    val spacing = LocalSpacing.current
    return this
        .fillMaxSize()
        .statusBarsPadding()
        .padding(start = pageStart(), end = spacing.page)
}

/** Space that keeps the last row above the floating dock. Apply it after verticalScroll, inside the scroll. */
@Composable
fun dockClearance(): Dp {
    val dock = LocalDockInsets.current
    val spacing = LocalSpacing.current
    return if (dock.bottom > 0.dp) dock.bottom + spacing.gapS else 0.dp
}

@Composable
fun pagePadding(): PaddingValues {
    val spacing = LocalSpacing.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return PaddingValues(
        start = pageStart(),
        end = spacing.page,
        top = top,
        bottom = dockClearance(),
    )
}
