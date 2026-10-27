package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 寄存器表（P8b2）的领域规则、行点位、写入换算、表单校验、工程文件读写（含旧版本兼容）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RegisterTableRulesTest {
    private fun base(
        area: RegisterArea = RegisterArea.HOLDING_REGISTER,
        type: DataType = DataType.U16,
        address: Int = 100,
        unit: Int = 1,
        conversion: Conversion = Conversion(),
        order: ByteWordOrder = ByteWordOrder.ABCD,
    ) = PointDefinition("p-1", "水泵参数", unit, area, address, type, order = order, conversion = conversion, unitLabel = "℃", pollMs = 500)

    private fun table(point: PointDefinition = base(), rows: Int = 4, writable: Boolean = false) =
        Widget("w1", WidgetKinds.REGISTER_TABLE, point = point, spanColumns = 2, tableRows = rows, tableWritable = writable)

    private fun project(vararg widgets: Widget) = Project("default", "x", listOf(Panel("p1", "面板", widgets = widgets.toList())))

    // ---- 行点位 ----

    @Test fun rowsAreIndependentPointsWithStridePerType() {
        val rows = table(base(type = DataType.U32), rows = 3).tablePoints
        assertEquals(listOf("p-1#0", "p-1#1", "p-1#2"), rows.map { it.id })
        assertEquals("32 位类型一行占 2 个寄存器", listOf(100, 102, 104), rows.map { it.address })
        assertTrue(rows.all { it.unitId == 1 && it.area == RegisterArea.HOLDING_REGISTER && it.type == DataType.U32 && it.pollMs == 500 })
        assertEquals(listOf(100, 101, 102, 103), table().tablePoints.map { it.address })
        assertEquals(listOf(5, 6), table(base(RegisterArea.COIL, DataType.BOOL, 5), rows = 2).tablePoints.map { it.address })
    }

    @Test fun tableRowsAreThePointsThatGetPolledAndMergeIntoOneRead() {
        val w = table(rows = 8)
        assertEquals(8, w.points.size)
        val groups = ReadPlan.build(project(w).usablePoints)
        assertEquals("相连的行合并成一次读", 1, groups.size)
        assertEquals(100, groups[0].start)
        assertEquals(8, groups[0].quantity)
    }

    // ---- 规则 ----

    @Test fun tableRulesUnitRowsTypesAndOverflow() {
        assertNull(WidgetWrites.problem(table()))
        assertNotNull("广播读不到", WidgetWrites.tableProblem(base(unit = 0), 4, false))
        assertNotNull(WidgetWrites.tableProblem(base(), 0, false))
        assertNotNull(WidgetWrites.tableProblem(base(), 17, false))
        assertNull(WidgetWrites.tableProblem(base(), 16, false))
        assertNotNull("64 位 / BCD / ASCII 不是表格能放的类型", WidgetWrites.tableProblem(base(type = DataType.U64), 4, false))
        assertNotNull(WidgetWrites.tableProblem(base(type = DataType.ASCII), 4, false))
        assertNotNull("整张表不能越过 65535", WidgetWrites.tableProblem(base(address = 65534, type = DataType.U16), 4, false))
        assertNull(WidgetWrites.tableProblem(base(address = 65532), 4, false))
        assertNotNull("32 位一行占 2 个", WidgetWrites.tableProblem(base(address = 65532, type = DataType.U32), 3, false))
        assertNull(WidgetWrites.tableProblem(base(RegisterArea.DISCRETE_INPUT, DataType.BOOL), 4, false))
        assertNotNull("线圈区域类型只能是 BOOL", WidgetWrites.tableProblem(base(RegisterArea.COIL, DataType.U16), 4, false))
    }

    @Test fun readOnlyAreasCannotBeWritable() {
        assertNotNull(WidgetWrites.tableProblem(base(RegisterArea.INPUT_REGISTER), 4, true))
        assertNotNull(WidgetWrites.tableProblem(base(RegisterArea.DISCRETE_INPUT, DataType.BOOL), 4, true))
        assertNull(WidgetWrites.tableProblem(base(RegisterArea.HOLDING_REGISTER), 4, true))
        assertNull(WidgetWrites.tableProblem(base(RegisterArea.COIL, DataType.BOOL), 4, true))
        assertFalse(table(base(RegisterArea.INPUT_REGISTER), writable = true).isUsable)
    }

    // ---- 写入换算 ----

    @Test fun rowWordsInvertScaleAndRoundIntegers() {
        val row = table(base(conversion = Conversion(0.1, 0.0, 1))).rowPoint(0)
        assertEquals(listOf(234), WidgetWrites.rowWords(row, 23.4).getOrThrow())
        assertEquals("整数类型四舍五入到最近整数", listOf(235), WidgetWrites.rowWords(row, 23.46).getOrThrow())
        assertEquals(listOf(0xFFFF), WidgetWrites.rowWords(table(base(type = DataType.S16)).rowPoint(0), -1.0).getOrThrow())
        assertEquals(listOf(0x3F80, 0x0000), WidgetWrites.rowWords(table(base(type = DataType.F32)).rowPoint(0), 1.0).getOrThrow())
        val cdab = table(base(type = DataType.U32, order = ByteWordOrder.CDAB)).rowPoint(0)
        assertEquals("字序 CDAB：低字在前", listOf(0x0000, 0x0001), WidgetWrites.rowWords(cdab, 65536.0).getOrThrow())
        val offset = table(base(conversion = Conversion(2.0, 10.0))).rowPoint(0)
        assertEquals("(30 - 10) / 2", listOf(10), WidgetWrites.rowWords(offset, 30.0).getOrThrow())
    }

    @Test fun rowWordsRejectOutOfRangeAndNonFiniteAndNonRegisterRows() {
        val u16 = table().rowPoint(0)
        assertTrue(WidgetWrites.rowWords(u16, 70000.0).isFailure)
        assertTrue("U16 不是负数", WidgetWrites.rowWords(u16, -1.0).isFailure)
        assertTrue(WidgetWrites.rowWords(u16, Double.NaN).isFailure)
        assertTrue(WidgetWrites.rowWords(u16, Double.POSITIVE_INFINITY).isFailure)
        assertTrue("线圈行不走寄存器编码", WidgetWrites.rowWords(table(base(RegisterArea.COIL, DataType.BOOL)).rowPoint(0), 1.0).isFailure)
        assertTrue("输入寄存器不能写", WidgetWrites.rowWords(table(base(RegisterArea.INPUT_REGISTER)).rowPoint(0), 1.0).isFailure)
    }

    @Test fun wordsOfIsTheWireOrderUsedByTheHexColumn() {
        val u32 = table(base(type = DataType.U32, order = ByteWordOrder.CDAB)).rowPoint(0)
        val raw = RawValue.Integer(65536, false)
        assertEquals(listOf(0x0000, 0x0001), WidgetWrites.wordsOf(u32, raw))
        assertNull(WidgetWrites.wordsOf(u32, null))
        assertEquals(listOf(0xFFFB), WidgetWrites.wordsOf(table(base(type = DataType.S16)).rowPoint(0), RawValue.Integer(-5, false)))
    }

    // ---- 表单 ----

    @Test fun formDefaultsRowsAndWritableOffByDefault() {
        val form = WidgetForm.forKind(WidgetKinds.REGISTER_TABLE).copy(name = "水泵参数")
        assertEquals("4", form.tableRows)
        assertFalse("默认不允许修改", form.tableWritable)
        val widget = form.build("w1", "p-1").widget!!
        assertEquals(4, widget.tableRows)
        assertFalse(widget.tableWritable)
        assertEquals(2, widget.spanColumns)
        assertEquals(1, widget.spanRows)
        assertEquals("表格不带阈值 / 量程", emptyList<ThresholdBand>(), widget.point!!.thresholds)
    }

    @Test fun formRowsValidationAndSpan() {
        val form = WidgetForm.forKind(WidgetKinds.REGISTER_TABLE).copy(name = "t")
        assertEquals(FormError.OUT_OF_RANGE, form.copy(tableRows = "0").build("w", "p").errors[FormField.ROWS])
        assertEquals(FormError.OUT_OF_RANGE, form.copy(tableRows = "17").build("w", "p").errors[FormField.ROWS])
        assertEquals(FormError.REQUIRED, form.copy(tableRows = "").build("w", "p").errors[FormField.ROWS])
        assertEquals(FormError.SPAN_OVERFLOW, form.copy(address = "65534", tableRows = "4").build("w", "p").errors[FormField.ADDRESS])
        assertEquals(FormError.SPAN_OVERFLOW, form.copy(address = "65530", type = DataType.U32, tableRows = "4").build("w", "p").errors[FormField.ADDRESS])
        assertTrue(form.copy(tableRows = "16").build("w", "p").ok)
        assertEquals("尺寸被固定", 2 to 1, form.copy(spanColumns = 1, spanRows = 2).build("w", "p").widget!!.let { it.spanColumns to it.spanRows })
    }

    @Test fun formWritableOnlyForCoilsAndHoldingAndFollowsAreaSwitch() {
        val form = WidgetForm.forKind(WidgetKinds.REGISTER_TABLE).copy(name = "t", tableWritable = true)
        assertTrue(form.canAllowWrite)
        assertTrue(form.build("w", "p").widget!!.tableWritable)
        val input = form.withArea(RegisterArea.INPUT_REGISTER)
        assertFalse("换到只读区域时自动关掉允许修改", input.tableWritable)
        assertFalse(input.canAllowWrite)
        assertFalse(input.copy(tableWritable = true).build("w", "p").widget!!.tableWritable)
        val coil = form.withArea(RegisterArea.COIL)
        assertEquals(DataType.BOOL, coil.type)
        assertTrue(coil.tableWritable)
        assertEquals(listOf(DataType.U16, DataType.S16, DataType.U32, DataType.S32, DataType.F32), form.allowedTypes())
        assertEquals(listOf(DataType.BOOL), coil.allowedTypes())
    }

    @Test fun formRoundTripThroughWidget() {
        val widget = table(base(type = DataType.S32, conversion = Conversion(0.01, 0.0, 2)), rows = 6, writable = true)
        val back = WidgetForm.fromWidget(widget)!!
        assertEquals("6", back.tableRows)
        assertTrue(back.tableWritable)
        assertEquals(widget.copy(title = ""), back.build("w1", "p-1").widget)
    }

    // ---- 工程文件 ----

    @Test fun jsonRoundTripAndVersionStaysOne() {
        val widgets = arrayOf(
            table(rows = 5, writable = true),
            table(base(RegisterArea.INPUT_REGISTER, DataType.F32, 0), rows = 2).copy(id = "w2", point = base(RegisterArea.INPUT_REGISTER, DataType.F32, 0).copy(id = "p-2")),
            table(base(RegisterArea.COIL, DataType.BOOL, 10).copy(id = "p-3"), rows = 8, writable = true).copy(id = "w3"),
        )
        val parsed = ProjectJson.decode(ProjectJson.encode(project(*widgets)))
        assertEquals(1, Project.FORMAT_VERSION)
        assertEquals(1, parsed.version)
        assertTrue(parsed.warnings.toString(), parsed.warnings.isEmpty())
        assertEquals(widgets.toList(), parsed.project.panels[0].widgets)
        assertEquals(5 + 2 + 8, parsed.project.usablePoints.size)
    }

    @Test fun invalidTablesInFileArePreservedNotRendered() {
        val obj = org.json.JSONObject(
            ProjectJson.encode(
                project(
                    table(),
                    table().copy(id = "w2", point = base().copy(id = "p-2")),
                    table().copy(id = "w3", point = base().copy(id = "p-3")),
                    table(base(RegisterArea.INPUT_REGISTER).copy(id = "p-4")).copy(id = "w4"),
                ),
            ),
        )
        val arr = obj.getJSONArray("panels").getJSONObject(0).getJSONArray("widgets")
        arr.getJSONObject(0).put("tableRows", 0)
        arr.getJSONObject(1).put("tableRows", 99)
        arr.getJSONObject(2).getJSONObject("point").put("address", 65534)
        arr.getJSONObject(3).put("tableWritable", true) // 输入寄存器不能允许修改
        val parsed = ProjectJson.decode(obj.toString())
        val widgets = parsed.project.panels[0].widgets
        assertTrue(widgets.none { it.isUsable })
        assertTrue(widgets.all { it.raw != null })
        assertEquals(4, parsed.warnings.size)
        val again = org.json.JSONObject(ProjectJson.encode(parsed.project)).getJSONArray("panels").getJSONObject(0).getJSONArray("widgets")
        assertEquals(arr.getJSONObject(3).toString(), again.getJSONObject(3).toString())
    }

    @Test fun oldFilesWithoutTableFieldsStillReadAndATableWithoutRowsIsPreserved() {
        val obj = org.json.JSONObject(ProjectJson.encode(project(table())))
        val w = obj.getJSONArray("panels").getJSONObject(0).getJSONArray("widgets").getJSONObject(0)
        w.remove("tableRows")
        val parsed = ProjectJson.decode(obj.toString())
        assertFalse(parsed.project.panels[0].widgets[0].isUsable)
        assertEquals(1, parsed.warnings.size)
    }

    @Test fun nonTableKindsIgnoreTableFields() {
        val plain = Widget("n", WidgetKinds.NUMBER, point = base())
        val obj = org.json.JSONObject(ProjectJson.encode(project(plain)))
        val w = obj.getJSONArray("panels").getJSONObject(0).getJSONArray("widgets").getJSONObject(0)
        w.put("tableRows", 5).put("tableWritable", true)
        val parsed = ProjectJson.decode(obj.toString()).project.panels[0].widgets[0]
        assertEquals(0, parsed.tableRows)
        assertFalse(parsed.tableWritable)
        assertEquals(1, parsed.points.size)
    }

    @Test fun tableMinColumnsIsTwo() {
        assertEquals(2, table().minColumns)
        assertEquals(1, Widget("n", WidgetKinds.NUMBER, point = base()).minColumns)
    }
}
