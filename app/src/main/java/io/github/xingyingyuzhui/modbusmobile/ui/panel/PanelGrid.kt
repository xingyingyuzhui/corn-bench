package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 排版结果：每一行是若干 (组件下标, 跨几列)；放不下就换行。跨列数夹到 [1, columns]。 */
internal fun packRows(spans: List<Int>, columns: Int): List<List<Pair<Int, Int>>> {
    val rows = mutableListOf<MutableList<Pair<Int, Int>>>()
    var used = 0
    spans.forEachIndexed { index, raw ->
        val span = raw.coerceIn(1, columns)
        if (rows.isEmpty() || used + span > columns) {
            rows.add(mutableListOf())
            used = 0
        }
        rows.last().add(index to span)
        used += span
    }
    return rows
}

/**
 * 稿面 `.wg`：N 列等宽、gap 8；跨 k 列的卡宽 = k×列宽 + (k−1)×gap；同一行的卡等高（取这一行最高的那张）。
 * 跨行（spanRows）在 P4 只表现为这一行更高，真正的两行并排留给编辑模式（P5）。
 */
@Composable
internal fun PanelGrid(
    columns: Int,
    spans: List<Int>,
    spanRows: List<Int>,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    item: @Composable (Int) -> Unit,
) {
    val rows = packRows(spans, columns)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (row in rows) {
            GridRow(columns, row.map { it.second }, row.map { spanRows[it.first] }, gap) {
                row.forEach { (index, _) -> item(index) }
            }
        }
    }
}

@Composable
private fun GridRow(
    columns: Int,
    spans: List<Int>,
    rowSpans: List<Int>,
    gap: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val total = constraints.maxWidth
        val cell = (total - gapPx * (columns - 1)) / columns
        val widths = spans.map { cell * it + gapPx * (it - 1) }
        // 同一行等高：先取各卡在自己宽度下的固有高度，跨行的卡按“两行高 + gap”的下限。
        val heights = measurables.mapIndexed { i, m ->
            val base = m.maxIntrinsicHeight(widths[i])
            if (rowSpans[i] > 1) base * rowSpans[i] + gapPx * (rowSpans[i] - 1) else base
        }
        val rowHeight = heights.maxOrNull() ?: 0
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(Constraints.fixed(widths[i], rowHeight))
        }
        layout(total, rowHeight) {
            var x = 0
            placeables.forEachIndexed { i, p ->
                p.placeRelative(x, 0)
                x += widths[i] + gapPx
            }
        }
    }
}
