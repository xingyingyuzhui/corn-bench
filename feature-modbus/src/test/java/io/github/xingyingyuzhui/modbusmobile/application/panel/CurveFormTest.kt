package io.github.xingyingyuzhui.modbusmobile.application.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveFormTest {
    private fun form(n: Int = 2): CurveForm {
        var f = CurveForm()
        repeat(n) { i -> f = f.addSeries("p-$i") }
        return f
    }

    @Test fun addSeriesPicksFreshColorsDefaultNamesAndStopsAtEight() {
        var f = CurveForm()
        for (i in 0 until 10) f = f.addSeries("p-$i")
        assertEquals(8, f.series.size)
        assertEquals((0 until 8).toList(), f.series.map { it.colorIndex })
        assertEquals("系列 1", f.series[0].name)
        assertEquals("系列 8", f.series[7].name)
        assertEquals(8, f.series.map { it.pointId }.toSet().size)
    }

    @Test fun deletingAFreesItsColorForTheNextOne() {
        var f = form(3).removeSeries(1)
        assertEquals(listOf(0, 2), f.series.map { it.colorIndex })
        f = f.addSeries("p-new")
        assertEquals(1, f.series.last().colorIndex)
    }

    @Test fun defaultsBuildAValidCurveWidget() {
        val r = form().build("w-1")
        assertTrue(r.ok)
        val w = r.widget!!
        assertEquals(WidgetKinds.CURVE, w.kind)
        assertEquals(2, w.spanColumns)
        val c = w.curve!!
        assertEquals(2, c.series.size)
        assertEquals(60, c.windowSec)
        assertEquals(60, c.retentionMinutes)
        assertEquals(20_000, c.maxPoints)
        assertTrue(c.left.auto && c.right.auto && c.grid && c.cursor)
        assertEquals(LegendPosition.TOP, c.legend)
        assertEquals(GapStyle.HATCH, c.gaps)
        assertTrue(w.isUsable)
        assertEquals(listOf("p-0", "p-1"), w.points.map { it.id })
        assertEquals("系列 1 / 系列 2", w.displayTitle)
    }

    @Test fun noSeriesIsRejected() {
        val r = CurveForm().build("w")
        assertFalse(r.ok)
        assertEquals(FormError.REQUIRED, r.errors[CurveField.SERIES])
    }

    @Test fun aBrokenSeriesIsFlaggedByIndex() {
        val f = form(3).updateSeries(1) { it.copy(point = it.point.copy(address = "70000")) }.updateSeries(2) { it.copy(point = it.point.copy(name = " ")) }
        val r = f.build("w")
        assertFalse(r.ok)
        assertEquals(setOf(1, 2), r.badSeries)
        assertNull(r.widget)
    }

    @Test fun customWindowNeedsAnIntegerNumberOfMinutes() {
        assertEquals(FormError.REQUIRED, form().copy(windowSec = null).build("w").errors[CurveField.WINDOW])
        assertEquals(FormError.NOT_INTEGER, form().copy(windowSec = null, customMinutes = "1.5").build("w").errors[CurveField.WINDOW])
        assertEquals(FormError.OUT_OF_RANGE, form().copy(windowSec = null, customMinutes = "2000").build("w").errors[CurveField.WINDOW])
        val ok = form().copy(windowSec = null, customMinutes = "30", retentionHours = "1").build("w")
        assertEquals(1800, ok.widget!!.curve!!.windowSec)
    }

    @Test fun windowCannotBeLongerThanRetention() {
        val r = form().copy(windowSec = 3600, retentionHours = "0.5").build("w")
        assertEquals(FormError.EXCEEDS_RETENTION, r.errors[CurveField.RETENTION])
        assertTrue(form().copy(windowSec = 3600, retentionHours = "1").build("w").ok)
    }

    @Test fun retentionAndMaxPointsAreValidated() {
        assertEquals(FormError.NOT_A_NUMBER, form().copy(retentionHours = "x").build("w").errors[CurveField.RETENTION])
        assertEquals(FormError.OUT_OF_RANGE, form().copy(retentionHours = "25").build("w").errors[CurveField.RETENTION])
        assertEquals(90, form().copy(windowSec = 60, retentionHours = "1.5").build("w").widget!!.curve!!.retentionMinutes)
        assertEquals(FormError.OUT_OF_RANGE, form().copy(maxPoints = "50").build("w").errors[CurveField.MAX_POINTS])
        assertEquals(FormError.NOT_INTEGER, form().copy(maxPoints = "100.5").build("w").errors[CurveField.MAX_POINTS])
        assertEquals(5000, form().copy(maxPoints = "5000").build("w").widget!!.curve!!.maxPoints)
    }

    @Test fun manualAxisNeedsMinBelowMax() {
        val f = form()
        assertEquals(FormError.REQUIRED, f.copy(leftAuto = false).build("w").errors[CurveField.LEFT_MIN])
        assertEquals(FormError.RANGE_ORDER, f.copy(leftAuto = false, leftMin = "30", leftMax = "10").build("w").errors[CurveField.LEFT_MAX])
        val ok = f.copy(leftAuto = false, leftMin = "10", leftMax = "30").build("w").widget!!.curve!!
        assertEquals(AxisRange(false, 10.0, 30.0), ok.left)
    }

    @Test fun manualRightAxisIsIgnoredWhenNoSeriesUsesIt() {
        val f = form().copy(rightAuto = false, rightMin = "", rightMax = "")
        assertTrue("右轴没系列用，空的手动范围不挡路", f.build("w").ok)
        val g = f.updateSeries(0) { it.copy(axis = YAxis.RIGHT) }
        assertEquals(FormError.REQUIRED, g.build("w").errors[CurveField.RIGHT_MIN])
    }

    @Test fun seriesStyleSurvivesBuild() {
        val f = form(1).updateSeries(0) {
            it.copy(colorIndex = 5, lineStyle = LineStyle.DOT, width = 3, axis = YAxis.RIGHT, showPoints = true, fill = true, step = true)
        }
        val s = f.build("w").widget!!.curve!!.series.single()
        assertEquals(5, s.colorIndex)
        assertEquals(LineStyle.DOT, s.lineStyle)
        assertEquals(3, s.width)
        assertEquals(YAxis.RIGHT, s.axis)
        assertTrue(s.showPoints && s.fill && s.step)
    }

    @Test fun axisUnitComesFromTheFirstSeriesOnThatAxis() {
        val f = form(2)
            .updateSeries(0) { it.copy(point = it.point.copy(unitLabel = "℃")) }
            .updateSeries(1) { it.copy(axis = YAxis.RIGHT, point = it.point.copy(unitLabel = "MPa")) }
        assertEquals("℃", f.axisUnit(YAxis.LEFT))
        assertEquals("MPa", f.axisUnit(YAxis.RIGHT))
        assertTrue(f.usesAxis(YAxis.RIGHT))
        assertFalse(form(1).usesAxis(YAxis.RIGHT))
    }

    @Test fun roundTripThroughAWidgetKeepsEveryField() {
        val f = form(2)
            .updateSeries(0) { it.copy(point = it.point.copy(name = "温度", address = "100", scale = "0.1", decimals = "1", unitLabel = "℃", type = DataType.S16)) }
            .updateSeries(1) { it.copy(point = it.point.copy(name = "压力", address = "101"), axis = YAxis.RIGHT, lineStyle = LineStyle.DASH) }
            .copy(windowSec = null, customMinutes = "90", retentionHours = "2", sampling = SamplingMode.SNAPSHOT, leftAuto = false, leftMin = "10", leftMax = "30",
                grid = false, legend = LegendPosition.BOTTOM, gaps = GapStyle.BREAK, cursor = false, spanRows = 2)
        val widget = f.build("w-1").widget!!
        val back = CurveForm.fromWidget(widget)!!
        assertEquals(f.series.map { it.point.name }, back.series.map { it.point.name })
        assertEquals(widget, back.build("w-1").widget)
        assertNull(back.windowSec)
        assertEquals("90", back.customMinutes)
        assertEquals("2", back.retentionHours)
        assertEquals(2, back.spanRows)
    }

    @Test fun fromWidgetRejectsNonCurveAndUnusable() {
        assertNull(CurveForm.fromWidget(Widget("w", WidgetKinds.NUMBER)))
        assertNull(CurveForm.fromWidget(Widget("w", WidgetKinds.CURVE, raw = "{}")))
    }

    @Test fun previewConfigUsesOnlyValidSeriesAndFallsBackOnABadWindow() {
        assertNull(CurveForm().previewConfig())
        val f = form(2).updateSeries(1) { it.copy(point = it.point.copy(address = "x")) }.copy(windowSec = null, customMinutes = "")
        val c = f.previewConfig()!!
        assertEquals(1, c.series.size)
        assertEquals(60, c.windowSec)
        assertNotNull(form().previewConfig())
    }

    @Test fun dataStepIgnoresManualAxesWhichBelongToTheDisplayStep() {
        val f = form()
        assertTrue(f.build("w").ok)
        val bad = f.copy(leftAuto = false, leftMin = "30", leftMax = "10")
        assertFalse(bad.build("w").ok)
        assertTrue(bad.buildData("w").ok)
        assertFalse("没有系列仍然拦在第 2 步", CurveForm().buildData("w").ok)
    }
}
