package io.github.xingyingyuzhui.modbusmobile.ui

import io.github.xingyingyuzhui.modbusmobile.ui.panel.packRows
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelGridTest {
    @Test fun singleColumnWidgetsFillRows() {
        assertEquals(listOf(listOf(0 to 1, 1 to 1), listOf(2 to 1)), packRows(listOf(1, 1, 1), 2))
    }

    @Test fun spanTwoTakesAWholeRowInTwoColumns() {
        assertEquals(listOf(listOf(0 to 1), listOf(1 to 2), listOf(2 to 1, 3 to 1)), packRows(listOf(1, 2, 1, 1), 2))
    }

    @Test fun spanThatDoesNotFitWrapsAndSpanIsClampedToColumns() {
        assertEquals(listOf(listOf(0 to 2, 1 to 1), listOf(2 to 2, 3 to 2)), packRows(listOf(2, 1, 2, 2), 4))
        assertEquals(listOf(listOf(0 to 2)), packRows(listOf(3), 2))
    }

    @Test fun fourColumns() {
        val rows = packRows(listOf(2, 1, 1, 1, 1, 1, 1), 4)
        assertEquals(listOf(listOf(0 to 2, 1 to 1, 2 to 1), listOf(3 to 1, 4 to 1, 5 to 1, 6 to 1)), rows)
    }

    @Test fun emptyHasNoRows() {
        assertEquals(emptyList<List<Pair<Int, Int>>>(), packRows(emptyList(), 2))
    }
}
