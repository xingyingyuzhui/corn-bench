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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelWizard
import io.github.xingyingyuzhui.modbusmobile.ui.panel.WizardActions
import io.github.xingyingyuzhui.modbusmobile.ui.panel.WizardState
import io.github.xingyingyuzhui.modbusmobile.ui.shell.DockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.PendingWrite
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.PlanResult
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.WriteConfirmSheet
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.WriteFormat
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.planWrite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** 向导里的寄存器表（P8b2）：行数、“允许面板修改”开关（只读区域锁定）、校验、预览里的 ✎ 不可点。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelRegisterTableWizardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private var state by mutableStateOf<WizardState?>(null)
    private val calls = mutableListOf<String>()

    private fun wizardState(step: Int, form: WidgetForm = WidgetForm(), showErrors: Boolean = false) =
        WizardState(step, form, "w-new", "p-new", showErrors = showErrors)

    private fun show(initial: WizardState, theme: ThemeMode = ThemeMode.DARK, fontScale: Float = 1f) {
        state = initial
        compose.setContent {
            val base = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER),
                LocalOpenConnection provides {},
                LocalShellMode provides ShellMode.PORTRAIT,
                LocalDockInsets provides DockInsets(),
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale),
            ) {
                ModbusTheme(UiSettings(theme = theme)) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        val s = state
                        if (s != null) {
                            PanelWizard(
                                s, null, true,
                                WizardActions(
                                    onKind = { kind -> calls += "kind:$kind"; state = s.copy(step = 2, form = WidgetForm.forKind(kind)) },
                                    onForm = { f -> state = s.copy(form = f) },
                                    onNext = { calls += "next" },
                                    onBack = { calls += "back" },
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

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun countText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun form() = state!!.form
    private fun touch(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().touchBoundsInRoot
    private val dp get() = compose.density.density

    private fun type(tag: String, text: String) =
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).performScrollTo().performTextReplacement(text)

    private fun table(area: RegisterArea = RegisterArea.HOLDING_REGISTER) =
        WidgetForm.forKind(WidgetKinds.REGISTER_TABLE).withArea(area).copy(name = "水泵参数", address = "100", tableRows = "3")

    @Test fun configHasRowsHintAndAWritableSwitchThatDefaultsOff() {
        show(wizardState(2, table()))
        compose.onNodeWithText("寄存器表组件", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(1, count("wiz-table-rows"))
        compose.onNodeWithTag("wiz-table-hint", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals("只读，默认关", false, form().tableWritable)
        runCatching { compose.onNodeWithTag("wiz-table-writable", useUnmergedTree = true).performScrollTo() }
        val t = touch("wiz-table-writable")
        assertTrue("开关行高 ${t.height / dp}", t.height >= 47.5f * dp)
        compose.onNodeWithTag("wiz-table-writable", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(true, form().tableWritable)
        compose.onNodeWithTag("wiz-write-note", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun readOnlyAreasLockTheWritableSwitch() {
        show(wizardState(2, table().copy(tableWritable = true)))
        compose.onNodeWithText("输入", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(RegisterArea.INPUT_REGISTER, form().area)
        assertEquals("切到输入寄存器时自动关掉", false, form().tableWritable)
        compose.onNodeWithTag("wiz-table-writable", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals("锁定时点了也不变", false, form().tableWritable)
    }

    @Test fun rowsMustBeBetweenOneAndSixteen() {
        show(wizardState(2, table().copy(tableRows = "17"), showErrors = true))
        compose.onNodeWithText("1–16", substring = true, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        type("wiz-table-rows", "16")
        assertEquals("16", form().tableRows)
        assertTrue(form().build("w", "p").ok)
        type("wiz-table-rows", "0")
        assertTrue(!form().build("w", "p").ok)
    }

    @Test fun lookStepHasNoSizeChooserAndThePreviewPencilsAreInert() {
        show(wizardState(3, table().copy(tableWritable = true)))
        assertEquals(1, count("wiz-table-look-note"))
        assertEquals("预览里画了 ✎ 的样子", 3, count("panel-row-edit-w-new-0") + count("panel-row-edit-w-new-1") + count("panel-row-edit-w-new-2"))
        compose.onNodeWithTag("panel-row-edit-w-new-0", useUnmergedTree = true).performClick()
        assertEquals(emptyList<String>(), calls)
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    @Test fun render_wizard_table_config_dark() { show(wizardState(2, table().copy(tableWritable = true))); shot("wizard_table_config_dark.png") }
    @Test fun render_wizard_table_readonly_light() { show(wizardState(2, table(RegisterArea.INPUT_REGISTER)), theme = ThemeMode.LIGHT); shot("wizard_table_readonly_light.png") }
    @Test fun render_wizard_table_look_dark() { show(wizardState(3, table().copy(tableWritable = true))); shot("wizard_table_look_dark.png") }
    @Test fun render_wizard_picker_p8b2_dark() { show(wizardState(1)); shot("wizard_picker_p8b2_dark.png") }
}
