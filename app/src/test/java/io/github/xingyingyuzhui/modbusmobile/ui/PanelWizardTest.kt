package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.compose.ui.test.hasText
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
import androidx.compose.ui.test.assertContentDescriptionContains
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

    // ---- 第 1 步：选类型（v3 分组、紧凑卡）----

    private fun open(tag: String) = compose.onNodeWithTag("$tag-toggle", useUnmergedTree = true).performScrollTo().performClick()

    @Test fun pickerGroupsReadOnlyKindsAndDeviceWritingKindsInCompactCards() {
        show(wizardState(1))
        for (kind in listOf("number", "curve", "lamp", "switch", "status_text", "progress", "gauge", "button", "register_table")) {
            val card = bounds("wiz-kind-$kind")
            assertTrue("$kind 卡高 ${card.height.value}", card.height.value >= 61.5f && card.height.value < 100f)
        }
        val a = bounds("wiz-kind-number")
        val b = bounds("wiz-kind-curve")
        val c = bounds("wiz-kind-lamp")
        assertEquals("列间距 8", 8f, (b.left - a.right).value, 0.6f)
        assertEquals("行间距 8", 8f, (c.top - a.bottom).value, 0.6f)
        assertEquals(a.width.value, b.width.value, 0.6f)
        assertEquals("K0 = space.page 16（CL-16）", 16f, a.left.value, 0.6f)
        // 寄存器表占一整行；开关 / 按钮在“控制设备”分组里，排在所有只读类型之后
        val table = bounds("wiz-kind-register_table")
        assertEquals(360f - 32f, table.width.value, 0.6f)
        val group = bounds("wiz-group-control")
        val sw = bounds("wiz-kind-switch")
        val btn = bounds("wiz-kind-button")
        assertTrue("控制分组在寄存器表下面", group.top >= table.bottom)
        assertTrue("开关 / 按钮在控制分组下面", sw.top >= group.bottom && btn.top == sw.top)
        compose.onNodeWithTag("wiz-group-control-tag", useUnmergedTree = true).assertTextContains("会写设备 · 每次都要确认")
        compose.onNodeWithText("只读，不会写设备", useUnmergedTree = true).assertIsDisplayed()
        // 9 种 + 两个分组标题一屏放下（不用滚动）
        assertTrue("按钮卡底部 ${btn.bottom.value}", btn.bottom.value <= 780f)
        compose.onNodeWithText("量程 + 色段", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("没有“后续版本”的占位说明", 0, count("wiz-more-later"))
        compose.onNodeWithTag("wiz-step", useUnmergedTree = true).assertContentDescriptionContains("第 1 步", substring = true)
        compose.onNodeWithText("添加组件", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("选类型页没有底栏", 0, count("wiz-next"))
    }

    @Test fun tappingAKindGoesToTheDataStep() {
        show(wizardState(1))
        compose.onNodeWithTag("wiz-kind-status_text", useUnmergedTree = true).performClick()
        assertEquals(listOf("kind:status_text"), calls)
        compose.onNodeWithTag("wiz-step", useUnmergedTree = true).assertContentDescriptionContains("第 2 步", substring = true)
        compose.onNodeWithText("状态文本 · 数据", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun backOnFirstStepLeaves() {
        show(wizardState(1))
        compose.onNodeWithTag("sub-back", useUnmergedTree = true).performClick()
        assertEquals(listOf("exit"), calls)
    }

    // ---- 第 2 步「数据」：数值 ----

    @Test fun numberDataStepOnlyAsksWhatToRead() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "供水温度", address = "100", type = DataType.S16, scale = "0.1", decimals = "1", unitLabel = "℃")))
        compose.onNodeWithText("数值 · 数据", useUnmergedTree = true).assertIsDisplayed()
        for (label in listOf("数据点位", "数据类型", "换算", "轮询周期", "Unit", "地址")) {
            assertTrue(label, compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
        // 名称 / 单位 / 小数位 / 阈值 / 量程都在第 3 步「显示」
        for (tag in listOf("wiz-name", "wiz-decimals", "wiz-unit-chip-℃", "wiz-threshold-add", "wiz-range-min")) assertEquals(tag, 0, count(tag))
        assertEquals("没有写入开关 / 记录历史这类没功能的东西", 0, compose.onAllNodesWithText("允许在面板上修改", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("记录历史", useUnmergedTree = true).fetchSemanticsNodes().size)
        // 换算、轮询折叠成一行摘要
        compose.onNodeWithTag("wiz-convert-summary", useUnmergedTree = true).assertTextContains("× 0.1 + 0")
        compose.onNodeWithTag("wiz-poll-block-summary", useUnmergedTree = true).assertTextContains("1000 ms（默认）")
        assertEquals("收起时看不到缩放框", 0, count("wiz-scale"))
        open("wiz-convert")
        compose.onNodeWithTag("wiz-formula").performScrollTo().assertTextContains("显示值 = 原始值 × 0.1 + 0", substring = true)
        // 地址后面有参考号
        compose.onNodeWithText("= 40101", useUnmergedTree = true).assertExists()
    }

    @Test fun onlyAllowedTypesAreDrawnAndRareOnesSitBehindMore() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")))
        // 保持寄存器下 BOOL 不画（不画灰的）；常用的在外面
        assertEquals(0, count("wiz-type-BOOL"))
        for (t in listOf("U16", "S16", "U32", "F32", "BIT")) assertEquals(t, 1, count("wiz-type-$t"))
        for (t in listOf("S32", "U64", "S64", "F64", "BCD16", "BCD32", "ASCII")) assertEquals("$t 在“更多”里", 0, count("wiz-type-$t"))
        compose.onNodeWithTag("wiz-type-more", useUnmergedTree = true).performScrollTo().performClick()
        for (t in listOf("S32", "U64", "S64", "F64", "BCD16", "BCD32", "ASCII")) assertEquals(t, 1, count("wiz-type-$t"))
        compose.onNodeWithTag("wiz-type-F32", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(DataType.F32, form().type)
    }

    @Test fun switchingToCoilForcesBoolAndShowsOccupiesOneCoil() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", type = DataType.S16)))
        compose.onNodeWithText("线圈", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(RegisterArea.COIL, form().area)
        assertEquals(DataType.BOOL, form().type)
        compose.onNodeWithTag("wiz-occupies", useUnmergedTree = true).assertTextContains("占用 1 个线圈")
        assertEquals("线圈下只有 BOOL，没有“更多”", 0, count("wiz-type-more"))
    }

    @Test fun occupiesFollowsTheType() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", type = DataType.U16)))
        compose.onNodeWithTag("wiz-occupies", useUnmergedTree = true).assertTextContains("占用 1 个寄存器")
        compose.onNodeWithTag("wiz-type-more", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("wiz-type-U64", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("wiz-occupies", useUnmergedTree = true).assertTextContains("占用 4 个寄存器")
    }

    @Test fun byteOrderOnlyAppearsFor32And64Bit() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", type = DataType.S16)))
        assertEquals("16 位时不画字序", 0, count("wiz-order"))
        compose.onNodeWithTag("wiz-type-U32", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("CDAB", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(ByteWordOrder.CDAB, form().order)
    }

    @Test fun bitAndAsciiExtraFieldsAppearOnlyForThoseTypes() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")))
        assertEquals(0, count("wiz-bit"))
        compose.onNodeWithTag("wiz-type-BIT", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("wiz-bit", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-type-more", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("wiz-type-ASCII", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("wiz-ascii", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("ASCII 是文字，不换算", 0, count("wiz-convert"))
    }

    @Test fun typingUpdatesTheFormAndFiltersJunk() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER)))
        type("wiz-address", "1a0b0")
        open("wiz-convert")
        type("wiz-scale", "0.1")
        type("wiz-offset", "-20")
        open("wiz-poll-block")
        type("wiz-poll", "500")
        assertEquals("地址只收数字", "100", form().address)
        assertEquals("0.1", form().scale)
        assertEquals("-20", form().offset)
        assertEquals("500", form().pollMs)
        compose.onNodeWithTag("wiz-poll-block-summary", useUnmergedTree = true).assertTextContains("500 ms")
    }

    @Test fun conversionOpensByItselfWhenItHasAnError() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", scale = "0")))
        // 缩放为 0 是输入错误，不等“下一步”就展开并提示（规范 40）
        compose.onNodeWithTag("wiz-scale", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("缩放不能为 0", useUnmergedTree = true).assertExists()
    }

    @Test fun formulaShowsTheLiveRawValue() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "温度", address = "100", type = DataType.S16, scale = "0.1", decimals = "1")))
        open("wiz-convert")
        compose.onNodeWithTag("wiz-formula").performScrollTo().assertTextContains("234 × 0.1 + 0 = 23.4", substring = true)
    }

    // ---- 预览 ----

    @Test fun dataStepPreviewIsOneLineWithLiveValueRawValueAndAddress() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "", address = "100", type = DataType.S16, scale = "0.1", decimals = "1", unitLabel = "℃")))
        // 名称还没填（在第 3 步）也照样显示实时读数
        compose.onNodeWithTag("wiz-preview-value", useUnmergedTree = true).assertTextContains("23.4")
        compose.onNodeWithTag("wiz-preview-raw", useUnmergedTree = true).assertTextContains("（原始 234）")
        compose.onNodeWithTag("wiz-preview-badge", useUnmergedTree = true).assertTextContains("预览")
        compose.onNodeWithText("U1 · HR100", useUnmergedTree = true).assertExists()
        assertTrue("一行高", bounds("wiz-preview-bar").height.value < 70f)
        assertEquals("有主站时不提示没连接", 0, count("wiz-preview-none"))
    }

    @Test fun previewWithoutMasterSaysWhyAndShowsNoFakeValue() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "供水温度")), live = false)
        compose.onNodeWithTag("wiz-preview-none", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-preview-value", useUnmergedTree = true).assertTextContains("—")
    }

    @Test fun displayStepPreviewUsesAPlaceholderNameUntilOneIsTyped() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "", address = "100")))
        // 第 3 步按实际尺寸画真卡片；没有名称时卡片先用占位，不写像输入框标签的“名称”
        compose.onNodeWithTag("panel-card-w-new", useUnmergedTree = true).assertExists()
        assertEquals(
            0,
            compose.onAllNodes(hasText("名称") and hasAnyAncestor(hasTestTag("panel-card-w-new")), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    // ---- 校验与导航 ----

    @Test fun errorsHideRequiredUntilNextButShowTypedJunkAtOnce() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "", unitId = "0", address = "")))
        assertEquals("必填不一上来就红", 0, compose.onAllNodesWithText("必填", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onAllNodesWithText("范围 1–247", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
    }

    @Test fun nextWithDataErrorsShowsThemAndLetsTheHostDecide() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "", address = ""), showErrors = true))
        assertTrue(compose.onAllNodesWithText("必填", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("wiz-next", useUnmergedTree = true).performClick()
        assertEquals(listOf("next"), calls)
    }

    @Test fun missingNameIsNotADataStepError() {
        // 名称在第 3 步：第 2 步即使 showErrors 也不报“名称必填”
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = ""), showErrors = true))
        assertEquals(0, compose.onAllNodesWithText("必填", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun savebarHasPrevAndNextWithDesignProportions() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")))
        val prev = bounds("wiz-prev")
        val next = bounds("wiz-next")
        assertEquals("上一步 : 下一步 = 1 : 1.6", 1.6f, next.width.value / prev.width.value, 0.05f)
        assertEquals(52f, prev.height.value, 0.6f)
        assertEquals(10f, (next.left - prev.right).value, 0.6f)
        assertEquals("K0 = space.page 16（CL-16）", 16f, prev.left.value, 0.6f)
        compose.onNodeWithText("下一步 · 显示", useUnmergedTree = true).assertIsDisplayed()
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

    // ---- 第 3 步「显示」----

    @Test fun displayStepHasNameDecimalsUnitAndThresholds() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "", address = "100")))
        compose.onNodeWithText("数值 · 显示", useUnmergedTree = true).assertIsDisplayed()
        type("wiz-name", "供水温度")
        type("wiz-decimals", "2a")
        assertEquals("供水温度", form().name)
        assertEquals("小数位只收数字", "2", form().decimals)
        compose.onNodeWithTag("wiz-threshold-add", useUnmergedTree = true).performScrollTo().assertExists()
        assertEquals("显示步没有数据点位", 0, count("wiz-address"))
        compose.onNodeWithText("添加到面板", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun unitChipsSelectToggleAndCustomWorks() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")))
        compose.onNodeWithTag("wiz-unit-chip-℃", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals("℃", form().unitLabel)
        compose.onNodeWithTag("wiz-unit-chip-℃", useUnmergedTree = true).performClick()
        assertEquals("再点一次取消", "", form().unitLabel)
        compose.onNodeWithTag("wiz-unit-chip-custom", useUnmergedTree = true).performScrollTo().performClick()
        type("wiz-unit-custom", "L/min")
        assertEquals("L/min", form().unitLabel)
    }

    @Test fun thresholdsCanBeAddedEditedAndDeleted() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x")))
        compose.onNodeWithTag("wiz-threshold-add", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(1, form().thresholds.size)
        type("wiz-threshold-value-0", "28")
        compose.onNodeWithTag("wiz-threshold-level-0", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("wiz-threshold-op-0", useUnmergedTree = true).performScrollTo().performClick()
        val row = form().thresholds[0]
        assertEquals("28", row.value)
        assertEquals(ThresholdLevel.ALERT, row.level)
        assertEquals(io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdOp.LT, row.op)
        compose.onNodeWithTag("wiz-threshold-delete-0", useUnmergedTree = true).performScrollTo().performClick()
        assertTrue(form().thresholds.isEmpty())
    }

    @Test fun thresholdAndStateControlsAreTouchSized() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", thresholds = listOf(ThresholdRow(value = "28")))))
        for (tag in listOf("wiz-threshold-add", "wiz-threshold-op-0", "wiz-threshold-level-0", "wiz-threshold-delete-0")) {
            compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
            val t = touch(tag)
            assertTrue("$tag 宽 ${t.width / dp}", t.width / dp >= 47.5f)
            assertTrue("$tag 高 ${t.height / dp}", t.height / dp >= 47.5f)
        }
    }

    @Test fun displayStepOffersSizeAndPreviewsAtThatSize() {
        val form = WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "供水温度", address = "100")
        show(wizardState(3, form))
        compose.onNodeWithTag("wiz-step", useUnmergedTree = true).assertContentDescriptionContains("第 3 步", substring = true)
        compose.onNodeWithTag("wiz-actual-size", useUnmergedTree = true).assertTextContains("面板里的其他组件")
        compose.onNodeWithText("2 × 1", useUnmergedTree = true).performClick()
        assertEquals(2, form().spanColumns)
        compose.onNodeWithText("2 × 2", useUnmergedTree = true).performClick()
        assertEquals(2, form().spanRows)
        // 预览卡宽度随尺寸变（假网格内边距 6；2 列宽时占满一整行）
        val grid = bounds("wiz-look-preview")
        assertEquals(grid.width.value, bounds("panel-card-w-new").width.value, 1f)
    }

    @Test fun displayStepPreviewIsOneColumnWideForOneByOne() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x", address = "1")))
        val grid = bounds("wiz-look-preview")
        // 1×1 只占两列网格里的一列：列宽 =（网格宽 − 列间距 6）/ 2
        assertEquals((grid.width.value - 6f) / 2, bounds("panel-card-w-new").width.value, 1f)
    }

    @Test fun displayStepOnEditSaysSaveChanges() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "x"), editing = "w-1"))
        compose.onNodeWithText("保存修改", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 其他类型 ----

    @Test fun lampDataStepHasNoConversionAndLabelsAreOnTheDisplayStep() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.LAMP).copy(name = "压缩机")))
        compose.onNodeWithText("指示灯 · 数据", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, count("wiz-convert"))
        assertEquals(0, count("wiz-on-label"))
        // 灯只能选位 / U16 / S16：F32 根本不画
        assertEquals(0, count("wiz-type-F32"))
        compose.activityRule.scenario.recreate()
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.LAMP).copy(name = "压缩机")))
        assertEquals(0, count("wiz-threshold-add"))
        type("wiz-on-label", "运行")
        assertEquals("运行", form().onLabel)
    }

    @Test fun statusMappingTableIsOnTheDisplayStep() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.STATUS_TEXT).copy(name = "运行模式")))
        compose.onNodeWithText("状态文本 · 显示", useUnmergedTree = true).assertIsDisplayed()
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
            compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
            val t = touch(tag)
            assertTrue("$tag ${t.width / dp}×${t.height / dp}", t.width / dp >= 47.5f && t.height / dp >= 47.5f)
        }
        compose.onNodeWithTag("wiz-state-delete-1", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(1, form().states.size)
    }

    @Test fun progressDisplayStepHasRangeAndUnit() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.PROGRESS).copy(name = "阀门开度")))
        compose.onNodeWithText("进度条 · 显示", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-range-min", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wiz-range-max", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun gaugeDisplayStepHasRangeAndAPreviewGauge() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.GAUGE).copy(name = "供水压力", address = "101", unitLabel = "MPa")))
        compose.onNodeWithText("仪表 · 显示", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-range-min", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wiz-range-max", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("预览卡是仪表", 1, count("panel-gauge-w-new"))
    }

    @Test fun gaugeRangeErrorShowsBelowTheField() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.GAUGE).copy(name = "x", address = "1", rangeMin = "5", rangeMax = "5"), showErrors = true))
        compose.onNodeWithTag("wiz-range-max", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("最小值要小于最大值", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 字体 1.3 / 横屏 ----

    @Test fun fontScale13KeepsDataFieldsAndButtonsUsable() {
        snapshot = ok(23.4, "23.4")
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = "供水温度", address = "100", type = DataType.S16, scale = "0.1")), fontScale = 1.3f)
        compose.onNodeWithTag("wiz-next", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(52f, bounds("wiz-next").height.value, 0.6f)
        val unit = bounds("wiz-unit")
        val addr = bounds("wiz-address")
        assertTrue("Unit 与地址不重叠", unit.right <= addr.left)
        val bar = bounds("wiz-preview-bar")
        val value = bounds("wiz-preview-value")
        assertTrue("预览值在预览条里", value.bottom.value <= bar.bottom.value && value.right.value <= bar.right.value)
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
        show(wizardState(3, form), theme = ThemeMode.LIGHT); shot("panel_wizard_status_light.png")
    }

    @Test fun render_wizard_lamp_dark() {
        val form = WidgetForm.forKind(WidgetKinds.LAMP).copy(name = "压缩机", address = "0", onLabel = "运行", offLabel = "停止")
        show(wizardState(3, form)); shot("panel_wizard_lamp_dark.png")
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
