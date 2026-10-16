package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetFormTest {
    private fun number(block: WidgetForm.() -> WidgetForm = { this }) =
        WidgetForm(kind = WidgetKinds.NUMBER, name = "供水温度", address = "100", type = DataType.S16, scale = "0.1", decimals = "1", unitLabel = "℃").block()

    private fun build(form: WidgetForm) = form.build("w-1", "p-1")

    @Test fun validNumberFormBuildsTheWidgetAndPoint() {
        val r = build(number())
        assertTrue(r.ok)
        val w = r.widget!!
        assertEquals("w-1", w.id)
        assertEquals(WidgetKinds.NUMBER, w.kind)
        val p = w.point!!
        assertEquals("p-1", p.id)
        assertEquals("供水温度", p.name)
        assertEquals(1, p.unitId)
        assertEquals(RegisterArea.HOLDING_REGISTER, p.area)
        assertEquals(100, p.address)
        assertEquals(DataType.S16, p.type)
        assertEquals(Conversion(0.1, 0.0, 1), p.conversion)
        assertEquals("℃", p.unitLabel)
        assertEquals(1000, p.pollMs)
        assertEquals(emptyList<PointIssue>(), p.validate())
    }

    @Test fun builtPointDecodesLikeTheDesignExample() {
        // 稿面：原始 234 × 0.1 + 0 = 23.4
        val p = build(number()).widget!!.point!!
        assertEquals("23.4", p.formatWords(intArrayOf(234)))
    }

    @Test fun requiredFieldsAreReported() {
        val r = build(number { copy(name = " ", address = "", unitId = "") })
        assertNull(r.widget)
        assertEquals(FormError.REQUIRED, r.errors[FormField.NAME])
        assertEquals(FormError.REQUIRED, r.errors[FormField.ADDRESS])
        assertEquals(FormError.REQUIRED, r.errors[FormField.UNIT])
    }

    @Test fun rangesAndTypesAreChecked() {
        assertEquals(FormError.OUT_OF_RANGE, build(number { copy(unitId = "0") }).errors[FormField.UNIT])
        assertEquals(FormError.OUT_OF_RANGE, build(number { copy(unitId = "248") }).errors[FormField.UNIT])
        assertEquals(FormError.OUT_OF_RANGE, build(number { copy(address = "65536") }).errors[FormField.ADDRESS])
        assertEquals(FormError.NOT_A_NUMBER, build(number { copy(address = "abc") }).errors[FormField.ADDRESS])
        assertEquals(FormError.NOT_INTEGER, build(number { copy(address = "1.5") }).errors[FormField.ADDRESS])
        assertEquals(FormError.OUT_OF_RANGE, build(number { copy(pollMs = "99") }).errors[FormField.POLL])
        assertEquals(FormError.OUT_OF_RANGE, build(number { copy(pollMs = "600001") }).errors[FormField.POLL])
        assertEquals(FormError.OUT_OF_RANGE, build(number { copy(decimals = "11") }).errors[FormField.DECIMALS])
    }

    @Test fun zeroScaleIsRejected() {
        assertEquals(FormError.ZERO_SCALE, build(number { copy(scale = "0") }).errors[FormField.SCALE])
        assertEquals(FormError.NOT_A_NUMBER, build(number { copy(scale = "x") }).errors[FormField.SCALE])
        assertEquals(FormError.NOT_A_NUMBER, build(number { copy(offset = "NaN") }).errors[FormField.OFFSET])
    }

    @Test fun addressPlusSizeMustFit() {
        val r = build(number { copy(address = "65535", type = DataType.U32) })
        assertEquals(FormError.SPAN_OVERFLOW, r.errors[FormField.ADDRESS])
        assertTrue(build(number { copy(address = "65534", type = DataType.U32) }).ok)
    }

    @Test fun bitAndAsciiFieldsOnlyCountForTheirTypes() {
        assertTrue("不是位类型时位号不校验", build(number { copy(bit = "99") }).ok)
        assertEquals(FormError.OUT_OF_RANGE, build(number { copy(type = DataType.BIT, bit = "16") }).errors[FormField.BIT])
        assertEquals(3, build(number { copy(type = DataType.BIT, bit = "3") }).widget!!.point!!.bit)
        assertEquals(FormError.OUT_OF_RANGE, build(number { copy(type = DataType.ASCII, asciiRegisters = "0") }).errors[FormField.ASCII])
        assertEquals(8, build(number { copy(type = DataType.ASCII, asciiRegisters = "8") }).widget!!.point!!.asciiRegisters)
    }

    @Test fun asciiAddressOverflowUsesTheLength() {
        val r = build(number { copy(type = DataType.ASCII, asciiRegisters = "10", address = "65530") })
        assertEquals(FormError.SPAN_OVERFLOW, r.errors[FormField.ADDRESS])
    }

    @Test fun thresholdsBecomeBands() {
        val form = number {
            copy(thresholds = listOf(ThresholdRow(ThresholdOp.GE, "28", ThresholdLevel.ALERT), ThresholdRow(ThresholdOp.LT, "10", ThresholdLevel.WARN)))
        }
        val bands = build(form).widget!!.point!!.thresholds
        assertEquals(listOf(ThresholdBand(ThresholdLevel.ALERT, min = 28.0), ThresholdBand(ThresholdLevel.WARN, max = 10.0)), bands)
        assertEquals(ThresholdLevel.ALERT, bands.levelFor(30.0))
        assertEquals(ThresholdLevel.WARN, bands.levelFor(5.0))
        assertEquals(ThresholdLevel.NORMAL, bands.levelFor(20.0))
    }

    @Test fun badThresholdRowIsFlaggedByIndex() {
        val form = number { copy(thresholds = listOf(ThresholdRow(value = "1"), ThresholdRow(value = ""), ThresholdRow(value = "x"))) }
        val r = build(form)
        assertEquals(setOf(1, 2), r.badThresholds)
        assertEquals(FormError.NOT_A_NUMBER, r.errors[FormField.THRESHOLD])
    }

    @Test fun progressNeedsOrderedRange() {
        val form = WidgetForm.forKind(WidgetKinds.PROGRESS).copy(name = "阀门开度", address = "130")
        val ok = build(form).widget!!.point!!
        assertEquals(0.0, ok.rangeMin!!, 0.0)
        assertEquals(100.0, ok.rangeMax!!, 0.0)
        assertEquals("%", ok.unitLabel)
        assertEquals(FormError.RANGE_ORDER, build(form.copy(rangeMin = "50", rangeMax = "50")).errors[FormField.RANGE_MAX])
        assertEquals(FormError.NOT_A_NUMBER, build(form.copy(rangeMin = "a")).errors[FormField.RANGE_MIN])
    }

    @Test fun numberCardIgnoresRangeAndLampIgnoresConversion() {
        val p = build(number { copy(rangeMin = "5", rangeMax = "1") }).widget!!.point!!
        assertNull("数值卡不用量程", p.rangeMin)
        val lamp = WidgetForm.forKind(WidgetKinds.LAMP).copy(name = "压缩机", scale = "0", unitLabel = "A", thresholds = listOf(ThresholdRow(value = "x")))
        val w = build(lamp).widget!!
        assertEquals(Conversion(), w.point!!.conversion)
        assertEquals("", w.point!!.unitLabel)
        assertTrue(w.point!!.thresholds.isEmpty())
    }

    @Test fun lampKeepsItsLabelsAndBitDefault() {
        val lamp = WidgetForm.forKind(WidgetKinds.LAMP).copy(name = "压缩机", onLabel = " 运行 ", offLabel = "停止", address = "0")
        val w = build(lamp).widget!!
        assertEquals(DataType.BIT, w.point!!.type)
        assertEquals("运行", w.onLabel)
        assertEquals("停止", w.offLabel)
        // 其他类型不带这两个字段
        assertEquals("", build(number { copy(onLabel = "运行") }).widget!!.onLabel)
    }

    @Test fun statusTextNeedsAtLeastOneMappingWithUniqueValues() {
        val form = WidgetForm.forKind(WidgetKinds.STATUS_TEXT).copy(name = "运行模式", address = "120")
        assertEquals(FormError.REQUIRED, build(form).errors[FormField.STATE])
        val rows = listOf(StateRow("0", "停止", StateTone.GRAY), StateRow("1", "运行", StateTone.GREEN), StateRow("2", "制冷", StateTone.BLUE))
        val w = build(form.copy(states = rows)).widget!!
        assertEquals(listOf(StateMapping(0, "停止", StateTone.GRAY), StateMapping(1, "运行", StateTone.GREEN), StateMapping(2, "制冷", StateTone.BLUE)), w.states)
        val dup = build(form.copy(states = rows + StateRow("1", "重复")))
        assertEquals(FormError.DUPLICATE, dup.errors[FormField.STATE])
        assertEquals(setOf(3), dup.badStates)
        val blankLabel = build(form.copy(states = listOf(StateRow("0", " "))))
        assertEquals(setOf(0), blankLabel.badStates)
        assertEquals(setOf(0), build(form.copy(states = listOf(StateRow("x", "停止")))).badStates)
        assertTrue("负数取值可以", build(form.copy(states = listOf(StateRow("-1", "反转")))).ok)
    }

    @Test fun sizeIsClampedAndKept() {
        assertEquals(2, build(number { copy(spanColumns = 2, spanRows = 2) }).widget!!.spanRows)
        assertEquals(1, build(number { copy(spanColumns = 0, spanRows = 0) }).widget!!.spanColumns)
    }

    @Test fun nameIsTrimmedAndCapped() {
        assertEquals("abc", build(number { copy(name = "  abc ") }).widget!!.point!!.name)
        assertEquals(WidgetForm.MAX_NAME, build(number { copy(name = "x".repeat(60)) }).widget!!.point!!.name.length)
    }

    // ---- 区域 / 类型联动 ----

    @Test fun bitAreasOnlyAllowBool() {
        for (kind in WidgetKinds.known) {
            for (area in listOf(RegisterArea.COIL, RegisterArea.DISCRETE_INPUT)) {
                assertEquals(listOf(DataType.BOOL), WidgetForm.allowedTypes(kind, area))
            }
        }
        assertFalse(DataType.BOOL in WidgetForm.allowedTypes(WidgetKinds.NUMBER, RegisterArea.HOLDING_REGISTER))
    }

    @Test fun changingAreaFixesTheType() {
        val coil = number().withArea(RegisterArea.COIL)
        assertEquals(DataType.BOOL, coil.type)
        val back = coil.withArea(RegisterArea.INPUT_REGISTER)
        assertEquals(DataType.U16, back.type)
        assertEquals(DataType.BIT, WidgetForm.forKind(WidgetKinds.LAMP).withArea(RegisterArea.COIL).withArea(RegisterArea.HOLDING_REGISTER).type)
        // 合法的类型不被改
        assertEquals(DataType.S16, number().withArea(RegisterArea.INPUT_REGISTER).type)
    }

    @Test fun disallowedTypesAreIgnoredAndOrderResetsFor16Bit() {
        assertEquals(DataType.S16, number().withType(DataType.BOOL).type)
        val wide = number().withType(DataType.F32).copy(order = ByteWordOrder.CDAB)
        assertEquals(ByteWordOrder.CDAB, wide.withType(DataType.U32).order)
        assertEquals(ByteWordOrder.ABCD, wide.withType(DataType.U16).order)
    }

    @Test fun statusTextAndLampOnlyOfferTheirTypes() {
        assertEquals(listOf(DataType.BIT, DataType.U16, DataType.S16), WidgetForm.allowedTypes(WidgetKinds.LAMP, RegisterArea.HOLDING_REGISTER))
        assertFalse(DataType.F32 in WidgetForm.allowedTypes(WidgetKinds.STATUS_TEXT, RegisterArea.HOLDING_REGISTER))
        assertFalse(DataType.ASCII in WidgetForm.allowedTypes(WidgetKinds.PROGRESS, RegisterArea.HOLDING_REGISTER))
        assertTrue(DataType.ASCII in WidgetForm.allowedTypes(WidgetKinds.NUMBER, RegisterArea.HOLDING_REGISTER))
    }

    // ---- 编辑已有组件：fromWidget → build 往返不丢字段 ----

    @Test fun roundTripKeepsEveryField() {
        val original = Widget(
            "w-9", WidgetKinds.NUMBER, point = PointDefinition(
                "p-9", "压力", 3, RegisterArea.INPUT_REGISTER, 200, DataType.F32, ByteWordOrder.CDAB,
                conversion = Conversion(0.01, -1.5, 2), unitLabel = "MPa",
                thresholds = listOf(ThresholdBand(ThresholdLevel.WARN, min = 0.8), ThresholdBand(ThresholdLevel.ALERT, max = 0.1)),
                pollMs = 500,
            ),
            spanColumns = 2, spanRows = 1,
        )
        val form = WidgetForm.fromWidget(original)!!
        assertEquals(original, form.build("w-9", "p-9").widget)
    }

    @Test fun roundTripKeepsLampStatusAndProgress() {
        val lamp = build(WidgetForm.forKind(WidgetKinds.LAMP).copy(name = "灯", onLabel = "运行", offLabel = "停止", address = "5")).widget!!
        assertEquals(lamp, WidgetForm.fromWidget(lamp)!!.build(lamp.id, lamp.point!!.id).widget)
        val status = build(
            WidgetForm.forKind(WidgetKinds.STATUS_TEXT).copy(name = "模式", states = listOf(StateRow("2", "制冷", StateTone.BLUE), StateRow("4", "故障", StateTone.RED))),
        ).widget!!
        assertEquals(status, WidgetForm.fromWidget(status)!!.build(status.id, status.point!!.id).widget)
        val progress = build(WidgetForm.forKind(WidgetKinds.PROGRESS).copy(name = "开度", rangeMin = "0", rangeMax = "50.5")).widget!!
        assertEquals(progress, WidgetForm.fromWidget(progress)!!.build(progress.id, progress.point!!.id).widget)
    }

    @Test fun unreadableWidgetCannotBeEdited() {
        assertNull(WidgetForm.fromWidget(Widget("w", "future_kind", raw = "{}")))
        assertNotNull(WidgetForm.fromWidget(build(number()).widget!!))
    }
}
