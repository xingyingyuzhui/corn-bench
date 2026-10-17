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

/** 开关 / 按钮（P8b1）的领域规则、表单校验、工程文件读写。ProjectJson 用 org.json，需要 Robolectric。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetWritesTest {
    private fun point(
        area: RegisterArea = RegisterArea.COIL,
        type: DataType = DataType.BOOL,
        unit: Int = 1,
        address: Int = 10,
        bit: Int = 0,
        order: ByteWordOrder = ByteWordOrder.ABCD,
    ) = PointDefinition("p1", "压缩机", unit, area, address, type, order = order, bit = bit)

    // ---- 开关规则 ----

    @Test fun switchOnlyCoilOrHoldingBitAndNeverBroadcast() {
        assertNull(WidgetWrites.switchProblem(point()))
        assertNull(WidgetWrites.switchProblem(point(RegisterArea.HOLDING_REGISTER, DataType.BIT, bit = 3)))
        assertNotNull("读不到状态的广播不能做开关", WidgetWrites.switchProblem(point(unit = 0)))
        assertNotNull(WidgetWrites.switchProblem(point(RegisterArea.DISCRETE_INPUT, DataType.BOOL)))
        assertNotNull(WidgetWrites.switchProblem(point(RegisterArea.INPUT_REGISTER, DataType.BIT)))
        assertNotNull("整个寄存器不是“一位”", WidgetWrites.switchProblem(point(RegisterArea.HOLDING_REGISTER, DataType.U16)))
    }

    // ---- 按钮预设值编码 ----

    @Test fun buttonCoilValueMustBeZeroOrOne() {
        assertNull(WidgetWrites.buttonProblem(point(), 1.0))
        assertNull(WidgetWrites.buttonProblem(point(), 0.0))
        assertNotNull(WidgetWrites.buttonProblem(point(), 2.0))
        assertNotNull(WidgetWrites.buttonProblem(point(), null))
        assertNotNull(WidgetWrites.buttonProblem(point(), Double.NaN))
    }

    @Test fun buttonRegisterWordsFollowTypeAndOrder() {
        val hr = RegisterArea.HOLDING_REGISTER
        assertEquals(listOf(500), WidgetWrites.buttonWords(point(hr, DataType.U16), 500.0))
        assertEquals(listOf(0xFFFF), WidgetWrites.buttonWords(point(hr, DataType.S16), -1.0))
        assertEquals(listOf(0x0001, 0x0000), WidgetWrites.buttonWords(point(hr, DataType.U32), 65536.0))
        assertEquals("字序 CDAB：低字在前", listOf(0x0000, 0x0001), WidgetWrites.buttonWords(point(hr, DataType.U32, order = ByteWordOrder.CDAB), 65536.0))
        assertEquals(listOf(0x3F80, 0x0000), WidgetWrites.buttonWords(point(hr, DataType.F32), 1.0))
        assertNull("超出 U16 范围", WidgetWrites.buttonWords(point(hr, DataType.U16), 70000.0))
        assertNull("U16 不能是负数", WidgetWrites.buttonWords(point(hr, DataType.U16), -1.0))
        assertNull("整数类型不接受小数", WidgetWrites.buttonWords(point(hr, DataType.U16), 1.5))
        assertNull("64 位 / 位 / ASCII 不是按钮能写的类型", WidgetWrites.buttonWords(point(hr, DataType.U64), 1.0))
        assertNull(WidgetWrites.buttonWords(point(hr, DataType.BIT), 1.0))
        assertNull(WidgetWrites.buttonWords(point(RegisterArea.INPUT_REGISTER, DataType.U16), 1.0))
    }

    @Test fun buttonAllowsBroadcastUnitZero() {
        assertNull(WidgetWrites.buttonProblem(point(unit = 0), 1.0))
        assertNotNull(WidgetWrites.buttonProblem(point(unit = 248), 1.0))
        assertNotNull(WidgetWrites.buttonProblem(point(RegisterArea.DISCRETE_INPUT, DataType.BOOL), 1.0))
    }

    // ---- 工程模型 ----

    @Test fun buttonIsNeverPolledButSwitchIs() {
        val button = Widget("b", WidgetKinds.BUTTON, point = point(unit = 0), writeValue = 1.0)
        val switch = Widget("s", WidgetKinds.SWITCH, point = point())
        assertTrue(button.isUsable)
        assertTrue(button.points.isEmpty())
        assertEquals(listOf(switch.point), switch.points)
        assertEquals(listOf(switch.point), Project("x", "x", listOf(Panel("p", "p", widgets = listOf(button, switch)))).usablePoints)
    }

    @Test fun unusableWhenRulesBroken() {
        assertFalse(Widget("b", WidgetKinds.BUTTON, point = point(), writeValue = null).isUsable)
        assertFalse(Widget("s", WidgetKinds.SWITCH, point = point(unit = 0)).isUsable)
    }

    // ---- 表单 ----

    @Test fun formDefaultsAndUnitRanges() {
        val sw = WidgetForm.forKind(WidgetKinds.SWITCH)
        assertEquals(RegisterArea.COIL, sw.area)
        assertEquals(DataType.BOOL, sw.type)
        assertTrue(sw.copy(name = "运行", unitId = "0").build("w", "p").errors.containsKey(FormField.UNIT))
        assertTrue(sw.copy(name = "运行", unitId = "248").build("w", "p").errors.containsKey(FormField.UNIT))
        assertTrue(sw.copy(name = "运行").build("w", "p").ok)

        val button = WidgetForm.forKind(WidgetKinds.BUTTON)
        assertEquals("1", button.writeValue)
        val broadcast = button.copy(name = "全部停机", unitId = "0").build("w", "p")
        assertTrue(broadcast.ok)
        assertEquals(0, broadcast.widget!!.point!!.unitId)
        assertTrue(button.copy(name = "x", unitId = "248").build("w", "p").errors.containsKey(FormField.UNIT))
        // 读取类组件仍然不能是 0
        assertTrue(WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", unitId = "0").build("w", "p").errors.containsKey(FormField.UNIT))
        assertTrue(WidgetForm.forKind(WidgetKinds.LAMP).copy(name = "x", unitId = "0").build("w", "p").errors.containsKey(FormField.UNIT))
    }

    @Test fun switchHoldingRegisterForcesBitType() {
        val form = WidgetForm.forKind(WidgetKinds.SWITCH).withArea(RegisterArea.HOLDING_REGISTER)
        assertEquals(DataType.BIT, form.type)
        assertEquals(listOf(DataType.BIT), form.allowedTypes())
        val widget = form.copy(name = "运行位", address = "140", bit = "3", onLabel = "运行", offLabel = "停止").build("w", "p").widget!!
        assertEquals(3, widget.point!!.bit)
        assertEquals("运行", widget.onLabel)
        assertEquals("停止", widget.offLabel)
        assertTrue(form.copy(name = "x", bit = "16").build("w", "p").errors.containsKey(FormField.BIT))
    }

    @Test fun buttonValueValidation() {
        val hr = WidgetForm.forKind(WidgetKinds.BUTTON).withArea(RegisterArea.HOLDING_REGISTER).copy(name = "设定", writeValue = "500")
        assertEquals(WidgetWrites.buttonTypes, hr.allowedTypes())
        assertEquals(500.0, hr.build("w", "p").widget!!.writeValue!!, 0.0)
        assertEquals(FormError.OUT_OF_RANGE, hr.copy(writeValue = "70000").build("w", "p").errors[FormField.WRITE_VALUE])
        assertEquals(FormError.NOT_INTEGER, hr.copy(writeValue = "1.5").build("w", "p").errors[FormField.WRITE_VALUE])
        assertEquals(FormError.NOT_A_NUMBER, hr.copy(writeValue = "abc").build("w", "p").errors[FormField.WRITE_VALUE])
        assertEquals(FormError.REQUIRED, hr.copy(writeValue = "").build("w", "p").errors[FormField.WRITE_VALUE])
        assertTrue("F32 可以是小数", hr.withType(DataType.F32).copy(writeValue = "1.5").build("w", "p").ok)
        val coil = WidgetForm.forKind(WidgetKinds.BUTTON).copy(name = "启动")
        assertEquals(1.0, coil.build("w", "p").widget!!.writeValue!!, 0.0)
        assertEquals(0.0, coil.copy(writeValue = "0").build("w", "p").widget!!.writeValue!!, 0.0)
        assertEquals(FormError.OUT_OF_RANGE, coil.copy(writeValue = "2").build("w", "p").errors[FormField.WRITE_VALUE])
    }

    @Test fun buttonCaptionAndFromWidgetRoundTrip() {
        val form = WidgetForm.forKind(WidgetKinds.BUTTON).copy(
            name = "启动压缩机", unitId = "0", address = "5", buttonLabel = "  启动  ", writeValue = "1",
        )
        val widget = form.build("w", "p").widget!!
        assertEquals("启动", widget.buttonLabel)
        val back = WidgetForm.fromWidget(widget)!!
        assertEquals("启动", back.buttonLabel)
        assertEquals("1", back.writeValue)
        assertEquals("0", back.unitId)
        assertEquals(widget, back.build("w", "p").widget)
        // 非按钮组件不带按钮字段
        assertEquals("", WidgetForm.forKind(WidgetKinds.SWITCH).copy(name = "x", buttonLabel = "y", writeValue = "1").build("w", "p").widget!!.buttonLabel)
    }

    // ---- 工程文件 ----

    private fun project(vararg widgets: Widget) = Project("default", "x", listOf(Panel("p1", "面板", widgets = widgets.toList())))

    @Test fun jsonRoundTripKeepsSwitchAndButtonAndVersionStaysOne() {
        val widgets = arrayOf(
            Widget("s1", WidgetKinds.SWITCH, point = point(), onLabel = "运行", offLabel = "停止"),
            Widget("s2", WidgetKinds.SWITCH, point = point(RegisterArea.HOLDING_REGISTER, DataType.BIT, bit = 3).copy(id = "p2")),
            Widget("b1", WidgetKinds.BUTTON, point = point().copy(id = "p3", unitId = 0), buttonLabel = "全停", writeValue = 0.0),
            Widget("b2", WidgetKinds.BUTTON, point = point(RegisterArea.HOLDING_REGISTER, DataType.U32, order = ByteWordOrder.CDAB).copy(id = "p4"), writeValue = 70000.0),
        )
        val text = ProjectJson.encode(project(*widgets))
        val parsed = ProjectJson.decode(text)
        assertEquals(1, Project.FORMAT_VERSION)
        assertEquals(1, parsed.version)
        assertTrue(parsed.warnings.toString(), parsed.warnings.isEmpty())
        assertEquals(widgets.toList(), parsed.project.panels[0].widgets)
        assertTrue(parsed.project.panels[0].widgets.all { it.isUsable })
        assertEquals("只有开关进轮询计划", 2, parsed.project.usablePoints.size)
    }

    @Test fun invalidSwitchOrButtonInFileIsPreservedNotRenderedAsWriter() {
        val good = ProjectJson.encode(
            project(
                Widget("s1", WidgetKinds.SWITCH, point = point()),
                Widget("b1", WidgetKinds.BUTTON, point = point().copy(id = "p2"), writeValue = 1.0),
                Widget("b2", WidgetKinds.BUTTON, point = point().copy(id = "p3"), writeValue = 1.0),
            ),
        )
        val obj = org.json.JSONObject(good)
        val arr = obj.getJSONArray("panels").getJSONObject(0).getJSONArray("widgets")
        arr.getJSONObject(0).getJSONObject("point").put("area", "DISCRETE_INPUT") // 离散输入只读，不能做开关
        arr.getJSONObject(1).remove("writeValue") // 按钮没有预设值
        arr.getJSONObject(2).put("writeValue", 7.0) // 线圈预设值只能 0 / 1
        val parsed = ProjectJson.decode(obj.toString())
        val widgets = parsed.project.panels[0].widgets
        assertTrue(widgets.none { it.isUsable })
        assertTrue(widgets.all { it.raw != null })
        assertEquals(3, parsed.warnings.size)
        // 保存再读：原样不丢
        val again = org.json.JSONObject(ProjectJson.encode(parsed.project)).getJSONArray("panels").getJSONObject(0).getJSONArray("widgets")
        assertEquals(arr.getJSONObject(2).toString(), again.getJSONObject(2).toString())
    }

    @Test fun broadcastUnitZeroIsOnlyAcceptedForButtons() {
        val obj = org.json.JSONObject(ProjectJson.encode(project(Widget("n", WidgetKinds.NUMBER, point = point(RegisterArea.HOLDING_REGISTER, DataType.U16)))))
        val w = obj.getJSONArray("panels").getJSONObject(0).getJSONArray("widgets").getJSONObject(0)
        w.getJSONObject("point").put("unitId", 0)
        val parsed = ProjectJson.decode(obj.toString())
        assertFalse(parsed.project.panels[0].widgets[0].isUsable)
        assertTrue(parsed.warnings.any { "UNIT_RANGE" in it })
    }
}
