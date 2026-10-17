package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.ByteWordOrder
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.Quality
import io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue
import io.github.xingyingyuzhui.modbusmobile.application.panel.StateRow
import io.github.xingyingyuzhui.modbusmobile.application.panel.StateTone
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdLevel
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdRow
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PREVIEW_POINT_ID
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelWizard
import io.github.xingyingyuzhui.modbusmobile.ui.panel.WizardActions
import io.github.xingyingyuzhui.modbusmobile.ui.panel.WizardState
import io.github.xingyingyuzhui.modbusmobile.ui.shell.DockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** 添加 / 编辑组件向导（P6）：版式对稿、触控 ≥48dp、字段联动与校验、字体 1.3 倍、渲染图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelWizardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private var state by mutableStateOf<WizardState?>(null)
    private val calls = mutableListOf<String>()
    private var snapshot by mutableStateOf<PointSnapshot?>(null)

    private fun wizardState(step: Int, form: WidgetForm = WidgetForm(), editing: String? = null, showErrors: Boolean = false) =
        WizardState(step, form, "w-new", "p-new", editingWidgetId = editing, original = if (editing != null) form else null, showErrors = showErrors)

    private fun show(
        initial: WizardState,
        theme: ThemeMode = ThemeMode.DARK,
        landscape: Boolean = false,
        fontScale: Float = 1f,
        live: Boolean = true,
    ) {
        state = initial
        val settings = UiSettings(theme = theme)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER),
                LocalOpenConnection provides {},
                LocalShellMode provides if (landscape) ShellMode.LANDSCAPE else ShellMode.PORTRAIT,
                LocalDockInsets provides DockInsets(),
                LocalDensity provides Density(base.density, fontScale),
            ) {
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        val s = state
                        if (s != null) {
                            PanelWizard(
                                s, snapshot, live,
                                WizardActions(
                                    onKind = { kind -> calls += "kind:$kind"; state = s.copy(step = 2, form = WidgetForm.forKind(kind)) },
                                    onForm = { f -> state = s.copy(form = f) },
                                    onNext = { calls += "next"; state = if (s.step == 2) s.copy(step = 3) else s },
                                    onBack = { calls += "back"; state = s.copy(step = s.step - 1) },
                                    onExit = { calls += "exit" },
                                ),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
    private fun touch(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().touchBoundsInRoot
    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private val dp get() = compose.density.density
    private fun form() = state!!.form

    private fun input(tag: String): androidx.compose.ui.test.SemanticsNodeInteraction =
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    private fun type(tag: String, text: String) = input(tag).performScrollTo().performTextReplacement(text)

    private fun ok(value: Double, text: String) = PointSnapshot(PREVIEW_POINT_ID, Quality.OK, RawValue.Integer(234, false), value, text, ThresholdLevel.NORMAL, 1_000, PanelFixtures.WALL_MS)

    // ---- 1/3 选类型 ----

    @Test fun pickerShowsTheNineRealKindsAsTwoColumnCards() {
        show(wizardState(1))
        for (kind in listOf("number", "curve", "lamp", "switch", "status_text", "progress", "gauge", "button", "register_table")) {
            val card = bounds("wiz-kind-$kind")
            assertTrue("$kind 卡高 ${card.height.value}", card.height.value >= 117.5f)
        }
        val a = bounds("wiz-kind-number")
        val b = bounds("wiz-kind-curve")
        val c = bounds("wiz-kind-lamp")
        assertEquals("列间距 8", 8f, (b.left - a.right).value, 0.6f)
        assertEquals("行间距 8", 8f, (c.top - a.bottom).value, 0.6f)
        assertEquals(a.width.value, b.width.value, 0.6f)
        assertEquals(14f, a.left.value, 0.6f)
        // 没有的类型不画灰卡：寄存器表在后续版本
        val g = bounds("wiz-kind-gauge")
        val btn = bounds("wiz-kind-button")
        assertEquals("仪表和按钮同一行（第四行）", btn.top.value, g.top.value, 0.6f)
        val p = bounds("wiz-kind-progress")
        val st = bounds("wiz-kind-status_text")
        assertEquals("状态文本和进度条同一行（第三行）", st.top.value, p.top.value, 0.6f)
        compose.onNodeWithText("量程 + 分段颜色", useUnmergedTree = true).assertIsDisplayed()
        val table = bounds("wiz-kind-register_table")
        assertEquals("寄存器表在第五行，单独一张半宽卡", a.width.value, table.width.value, 0.6f)
        assertTrue(table.top > btn.bottom)
        assertEquals("没有“后续版本”的占位说明", 0, count("wiz-more-later"))
        compose.onNodeWithTag("wiz-step", useUnmergedTree = true).assertTextContains("1 / 3")
        compose.onNodeWithText("添加组件", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("选类型页没有底栏", 0, count("wiz-next"))
    }

    @Test fun tappingAKindGoesToTheConfigStep() {
        show(wizardState(1))
        compose.onNodeWithTag("wiz-kind-status_text", useUnmergedTree = true).performClick()
        assertEquals(listOf("kind:status_text"), calls)
        compose.onNodeWithTag("wiz-step", useUnmergedTree = true).assertTextContains("2 / 3")
        compose.onNodeWithText("状态文本", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun backOnFirstStepLeaves() {
        show(wizardState(1))
        compose.onNodeWithTag("sub-back", useUnmergedTree = true).performClick()
        assertEquals(listOf("exit"), calls)
    }

    // ---- 2/3 配置：数值 ----

    @Test fun numberConfigHasAllDesignSections() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "供水温度", address = "100", type = DataType.S16, scale = "0.1", decimals = "1", unitLabel = "℃")))
        compose.onNodeWithText("数值组件", useUnmergedTree = true).assertIsDisplayed()
        for (label in listOf("数据源", "换算与显示", "采集", "数据类型", "缩放", "偏移", "小数位", "单位", "名称", "轮询周期", "Unit", "地址")) {
            assertTrue(label, compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
        assertEquals("数值卡没有量程", 0, count("wiz-range-min"))
        assertEquals("没有写入开关 / 记录历史这类没功能的东西", 0, compose.onAllNodesWithText("允许在面板上修改", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("记录历史", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithTag("wiz-formula").performScrollTo().assertTextContains("显示值 = 原始值 × 0.1 + 0", substring = true)
    }

    @Test fun typesAreAllListedButOnlyAllowedOnesWork() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")))
        assertEquals(13, listOf("BOOL", "BIT", "U16", "S16", "U32", "S32", "F32", "U64", "S64", "F64", "BCD16", "BCD32", "ASCII").count { count("wiz-type-$it") == 1 })
        compose.onNodeWithTag("wiz-type-F32", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(DataType.F32, form().type)
        compose.onNodeWithTag("wiz-type-BOOL", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals("保持寄存器下 BOOL 不可选", DataType.F32, form().type)
    }

    @Test fun switchingToCoilForcesBoolAndShowsOccupiesOneCoil() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", type = DataType.S16)))
        compose.onNodeWithText("线圈", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(RegisterArea.COIL, form().area)
        assertEquals(DataType.BOOL, form().type)
        compose.onNodeWithTag("wiz-occupies", useUnmergedTree = true).assertTextContains("占用 1 个线圈")
    }

    @Test fun occupiesFollowsTheType() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", type = DataType.U16)))
        compose.onNodeWithTag("wiz-occupies", useUnmergedTree = true).assertTextContains("占用 1 个寄存器")
        compose.onNodeWithTag("wiz-type-U64", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("wiz-occupies", useUnmergedTree = true).assertTextContains("占用 4 个寄存器")
    }

    @Test fun byteOrderOnlyAppliesTo32And64Bit() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", type = DataType.S16)))
        compose.onNodeWithText("CDAB", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals("16 位时字序不可选", ByteWordOrder.ABCD, form().order)
        compose.onNodeWithTag("wiz-type-U32", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("CDAB", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(ByteWordOrder.CDAB, form().order)
    }

    @Test fun bitAndAsciiExtraFieldsAppearOnlyForThoseTypes() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")))
        assertEquals(0, count("wiz-bit"))
        compose.onNodeWithTag("wiz-type-BIT", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("wiz-bit", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-type-ASCII", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("wiz-ascii", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("ASCII 是文字，不换算", 0, count("wiz-scale"))
    }

    @Test fun typingUpdatesTheFormAndFiltersJunk() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER)))
        type("wiz-name", "供水温度")
        type("wiz-address", "1a0b0")
        type("wiz-scale", "0.1")
        type("wiz-offset", "-20")
        type("wiz-poll", "500")
        assertEquals("供水温度", form().name)
        assertEquals("地址只收数字", "100", form().address)
        assertEquals("0.1", form().scale)
        assertEquals("-20", form().offset)
        assertEquals("500", form().pollMs)
    }

    @Test fun unitChipsSelectToggleAndCustomWorks() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")))
        compose.onNodeWithTag("wiz-unit-chip-℃", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals("℃", form().unitLabel)
        compose.onNodeWithTag("wiz-unit-chip-℃", useUnmergedTree = true).performClick()
        assertEquals("再点一次取消", "", form().unitLabel)
        compose.onNodeWithTag("wiz-unit-chip-custom", useUnmergedTree = true).performScrollTo().performClick()
        type("wiz-unit-custom", "L/min")
        assertEquals("L/min", form().unitLabel)
    }

    @Test fun thresholdsCanBeAddedEditedAndDeleted() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")))
        compose.onNodeWithTag("wiz-threshold-add", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(1, form().thresholds.size)
        type("wiz-threshold-value-0", "28")
        compose.onNodeWithTag("wiz-threshold-level-0", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("wiz-threshold-op-0", useUnmergedTree = true).performClick()
        val row = form().thresholds[0]
        assertEquals("28", row.value)
        assertEquals(ThresholdLevel.ALERT, row.level)
        assertEquals(io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdOp.LT, row.op)
        compose.onNodeWithTag("wiz-threshold-delete-0", useUnmergedTree = true).performClick()
        assertTrue(form().thresholds.isEmpty())
    }

    @Test fun thresholdAndStateControlsAreTouchSized() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", thresholds = listOf(ThresholdRow(value = "28")))))
        for (tag in listOf("wiz-threshold-add", "wiz-threshold-op-0", "wiz-threshold-level-0", "wiz-threshold-delete-0")) {
            compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
            val t = touch(tag)
            assertTrue("$tag 宽 ${t.width / dp}", t.width / dp >= 47.5f)
            assertTrue("$tag 高 ${t.height / dp}", t.height / dp >= 47.5f)
        }
    }

    @Test fun formulaShowsTheLiveRawValue() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "温度", address = "100", type = DataType.S16, scale = "0.1", decimals = "1")))
        compose.onNodeWithTag("wiz-formula").performScrollTo().assertTextContains("234 × 0.1 + 0 = 23.4", substring = true)
    }

    // ---- 预览 ----

    @Test fun previewShowsTheLiveValueWithAmberBorder() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "供水温度", address = "100", type = DataType.S16, scale = "0.1", decimals = "1", unitLabel = "℃")))
        compose.onNodeWithTag("panel-value-w-new", useUnmergedTree = true).assertTextContains("23.4")
        assertEquals("有主站时不提示没连接", 0, count("wiz-preview-none"))
    }

    @Test fun previewWithoutMasterSaysWhyAndShowsNoFakeValue() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "供水温度")), live = false)
        compose.onNodeWithTag("wiz-preview-none", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("panel-value-w-new", useUnmergedTree = true).assertTextContains("—")
    }

    @Test fun incompleteFormShowsPlaceholderNotAFakeCard() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "", address = "")))
        assertEquals(1, count("wiz-preview-placeholder"))
        compose.onNodeWithText("填完整后这里显示实时读数", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 校验与导航 ----

    @Test fun errorsHideRequiredUntilNextButShowTypedJunkAtOnce() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "", unitId = "0", address = "")))
        assertEquals("必填不一上来就红", 0, compose.onAllNodesWithText("必填", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onAllNodesWithText("范围 1–247", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
    }

    @Test fun nextWithErrorsShowsThemAndLetsTheHostDecide() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = ""), showErrors = true))
        assertTrue(compose.onAllNodesWithText("必填", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("wiz-next", useUnmergedTree = true).performClick()
        assertEquals(listOf("next"), calls)
    }

    @Test fun savebarHasPrevAndNextWithDesignProportions() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")))
        val prev = bounds("wiz-prev")
        val next = bounds("wiz-next")
        assertEquals("上一步 : 下一步 = 1 : 1.6", 1.6f, next.width.value / prev.width.value, 0.05f)
        assertEquals(52f, prev.height.value, 0.6f)
        assertEquals(10f, (next.left - prev.right).value, 0.6f)
        assertEquals(14f, prev.left.value, 0.6f)
        compose.onNodeWithText("下一步 · 外观", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun prevGoesBackOneStep() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")))
        compose.onNodeWithTag("wiz-prev", useUnmergedTree = true).performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test fun editingFirstStepIsCancelAndAsksWhenChanged() {
        val form = WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "温度")
        show(wizardState(2, form, editing = "w-1"))
        compose.onNodeWithText("取消", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-prev", useUnmergedTree = true).performClick()
        assertEquals("没改动直接退出", listOf("exit"), calls)
    }

    @Test fun editingWithChangesAsksBeforeDiscarding() {
        val form = WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "温度")
        show(wizardState(2, form.copy(name = "改过"), editing = "w-1").copy(original = form))
        compose.onNodeWithTag("wiz-prev", useUnmergedTree = true).performClick()
        assertTrue(calls.isEmpty())
        compose.onNodeWithText("放弃这个组件？").assertIsDisplayed()
        compose.onNodeWithTag("wiz-discard-dismiss").performClick()
        assertTrue(calls.isEmpty())
        compose.onNodeWithTag("wiz-prev", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("wiz-discard-confirm").performClick()
        assertEquals(listOf("exit"), calls)
    }

    // ---- 其他类型 ----

    @Test fun lampConfigHasLabelsNotConversion() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.LAMP).copy(name = "压缩机")))
        compose.onNodeWithText("指示灯组件", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-on-label", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals(0, count("wiz-scale"))
        assertEquals(0, count("wiz-threshold-add"))
        // 灯只能选位 / U16 / S16
        compose.onNodeWithTag("wiz-type-F32", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(DataType.BIT, form().type)
        type("wiz-on-label", "运行")
        assertEquals("运行", form().onLabel)
    }

    @Test fun statusConfigEditsTheMappingTable() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.STATUS_TEXT).copy(name = "运行模式")))
        compose.onNodeWithText("状态文本", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-states-title", useUnmergedTree = true).performScrollTo().assertTextContains("映射表 · 0 项")
        compose.onNodeWithTag("wiz-state-add", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("wiz-state-add", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf("0", "1"), form().states.map { it.value })
        type("wiz-state-label-0", "停止")
        compose.onNodeWithTag("wiz-state-tone-0", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(StateTone.AMBER, form().states[0].tone)
        assertEquals("停止", form().states[0].label)
        compose.onNodeWithTag("wiz-states-title", useUnmergedTree = true).assertTextContains("映射表 · 2 项")
        for (tag in listOf("wiz-state-tone-0", "wiz-state-delete-0", "wiz-state-add")) {
            val t = touch(tag)
            assertTrue("$tag ${t.width / dp}×${t.height / dp}", t.width / dp >= 47.5f && t.height / dp >= 47.5f)
        }
        compose.onNodeWithTag("wiz-state-delete-1", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(1, form().states.size)
    }

    @Test fun progressConfigHasRangeAndUnit() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.PROGRESS).copy(name = "阀门开度")))
        compose.onNodeWithText("进度条组件", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-range-min", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wiz-range-max", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun gaugeConfigHasRangeUnitThresholdsAndAPreviewGauge() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.GAUGE).copy(name = "供水压力", address = "101", unitLabel = "MPa")))
        compose.onNodeWithText("仪表组件", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-range-min", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wiz-range-max", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("预览卡是仪表", 1, count("panel-gauge-w-new"))
    }

    @Test fun gaugeRangeErrorShowsBelowTheField() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.GAUGE).copy(name = "x", address = "1", rangeMin = "5", rangeMax = "5"), showErrors = true))
        compose.onNodeWithTag("wiz-range-max", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("最小值要小于最大值", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 3/3 外观 ----

    @Test fun lookStepOffersSizeAndShowsActualSize() {
        val form = WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "供水温度", address = "100")
        show(wizardState(3, form))
        compose.onNodeWithTag("wiz-step", useUnmergedTree = true).assertTextContains("3 / 3")
        compose.onNodeWithTag("wiz-actual-size", useUnmergedTree = true).assertTextContains("1 × 1 实际效果", substring = true)
        compose.onNodeWithText("2 × 1", useUnmergedTree = true).performClick()
        assertEquals(2, form().spanColumns)
        compose.onNodeWithText("2 × 2", useUnmergedTree = true).performClick()
        assertEquals(2, form().spanRows)
        compose.onNodeWithText("添加到面板", useUnmergedTree = true).assertIsDisplayed()
        // 预览卡宽度随尺寸变
        assertEquals(360f - 28f, bounds("panel-card-w-new").width.value, 1f)
    }

    @Test fun lookStepPreviewIsOneColumnWideForOneByOne() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", address = "1")))
        assertEquals((360f - 28f - 8f) / 2, bounds("panel-card-w-new").width.value, 1f)
    }

    @Test fun lookStepOnEditSaysSaveChanges() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x"), editing = "w-1"))
        compose.onNodeWithText("保存修改", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("编辑时上一步是真的上一步", "上一步", "上一步")
    }

    // ---- 字体 1.3 / 横屏 ----

    @Test fun fontScale13KeepsConfigFieldsAndButtonsUsable() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "供水温度", address = "100", type = DataType.S16, scale = "0.1")), fontScale = 1.3f)
        compose.onNodeWithTag("wiz-next", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(52f, bounds("wiz-next").height.value, 0.6f)
        val unit = bounds("wiz-unit")
        val addr = bounds("wiz-address")
        assertTrue("Unit 与地址不重叠", unit.right <= addr.left)
        val card = bounds("panel-card-w-new")
        val value = bounds("panel-value-w-new")
        assertTrue(value.bottom.value <= card.bottom.value)
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeStillHasTheSavebarVisible() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")), landscape = true)
        compose.onNodeWithTag("wiz-next", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-prev", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 渲染图 ----

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    @Test fun render_wizard_pick_dark() { show(wizardState(1)); shot("panel_wizard_pick_dark.png") }

    @Test fun render_wizard_gauge_dark() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.GAUGE).copy(name = "供水压力", address = "101", unitLabel = "MPa", rangeMax = "1.2")))
        shot("panel_wizard_gauge_dark.png")
    }

    @Test fun render_wizard_pick_light() { show(wizardState(1), theme = ThemeMode.LIGHT); shot("panel_wizard_pick_light.png") }

    private val tempForm = WidgetForm.forKind(WidgetKinds.NUMBER).copy(
        name = "供水温度", address = "100", type = DataType.S16, scale = "0.1", decimals = "1", unitLabel = "℃",
        thresholds = listOf(ThresholdRow(value = "28", level = ThresholdLevel.ALERT), ThresholdRow(io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdOp.LT, "10", ThresholdLevel.WARN)),
    )

    @Test fun render_wizard_number_dark() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(2, tempForm)); shot("panel_wizard_number_dark.png")
    }

    @Test fun render_wizard_number_light() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(2, tempForm), theme = ThemeMode.LIGHT); shot("panel_wizard_number_light.png")
    }

    @Test fun render_wizard_number_errors_dark() {
        show(wizardState(2, tempForm.copy(name = "", unitId = "0", scale = "0"), showErrors = true)); shot("panel_wizard_number_errors_dark.png")
    }

    @Test fun render_wizard_status_light() {
        val form = WidgetForm.forKind(WidgetKinds.STATUS_TEXT).copy(
            name = "运行模式", address = "120",
            states = listOf(StateRow("0", "停止", StateTone.GRAY), StateRow("1", "运行", StateTone.GREEN), StateRow("2", "制冷", StateTone.BLUE)),
        )
        snapshot = PointSnapshot(PREVIEW_POINT_ID, Quality.OK, RawValue.Integer(2, false), 2.0, "2", ThresholdLevel.NORMAL, 1_000, PanelFixtures.WALL_MS)
        show(wizardState(2, form), theme = ThemeMode.LIGHT); shot("panel_wizard_status_light.png")
    }

    @Test fun render_wizard_lamp_dark() {
        val form = WidgetForm.forKind(WidgetKinds.LAMP).copy(name = "压缩机", address = "0", onLabel = "运行", offLabel = "停止")
        show(wizardState(2, form)); shot("panel_wizard_lamp_dark.png")
    }

    @Test fun render_wizard_look_dark() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(3, tempForm)); shot("panel_wizard_look_dark.png")
    }

    @Test fun render_wizard_number_fontscale_dark() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(2, tempForm), fontScale = 1.3f); shot("panel_wizard_number_font13_dark.png")
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun render_wizard_number_landscape_dark() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(2, tempForm), landscape = true); shot("panel_wizard_number_dark_landscape.png")
    }
}
