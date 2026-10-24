package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PanelRuntime
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointStore
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollScheduler
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectLoad
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointPreview
import androidx.compose.ui.test.onFirst
import io.github.xingyingyuzhui.modbusmobile.application.panel.Conversion
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveConfig
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveSeries
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.application.panel.YAxis
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** 曲线向导（P7）端到端：真 ViewModel + 真工程文件 + 真轮询 / 样本历史；全程只读不写，样本只在内存里。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelCurveFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    /** 保持寄存器 100 读 234、101 读 82，其余读 0。 */
    private class Session(role: Role? = Role.MASTER) : PanelFakeSession(role) {
        val requests = CopyOnWriteArrayList<ModbusRequest>()
        override suspend fun execute(request: ModbusRequest): ModbusResult {
            requests += request
            val r = request as? ModbusRequest.ReadHoldingRegisters
                ?: return ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(0)))
            val values = (0 until r.quantity).map { i ->
                when (r.address + i) { 100 -> 234; 101 -> 82; else -> 0 }
            }
            return ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, values))
        }
    }

    private val session = Session()
    private val dir get() = File(tmp.root, "projects")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val history = SampleHistory()

    @After fun tearDown() { scope.cancel() }

    private fun repoWith(project: Project): ProjectRepository = ProjectRepository(dir).also { it.save(project) }

    private fun emptyProject() = Project("default", "x", listOf(Panel("p1", "机房 A")))

    private fun runtime(): PanelRuntime {
        val store = PointStore()
        val clock = { System.nanoTime() / 1_000_000 }
        return PanelRuntime(store, PollScheduler(session, scope, store, nowMs = clock, history = history), history) { s -> PointPreview(session, s, clock) }
    }

    private fun show(repository: ProjectRepository, runtime: PanelRuntime = runtime()) {
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides MemorySettingsRepository(settings),
                LocalProjectRepository provides repository,
                LocalPanelRuntime provides runtime,
            ) { ModbusTheme(settings) { ModbusApp(startRoute = Destination.Panel.route) } }
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun waitTag(tag: String) = compose.waitUntil(5_000) { count(tag) > 0 }
    private fun waitGone(tag: String) = compose.waitUntil(5_000) { count(tag) == 0 }
    private fun reloaded(): Project = (ProjectRepository(dir).load() as ProjectLoad.Loaded).project
    private fun tap(tag: String) {
        val n = compose.onNodeWithTag(tag, useUnmergedTree = true)
        runCatching { n.performScrollTo() }
        n.performClick()
    }
    private fun type(tag: String, text: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
        .also { n -> runCatching { n.performScrollTo() } }.performTextReplacement(text)
    private fun waitText(text: String, ms: Long = 8_000) =
        compose.waitUntil(ms) { compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tagStarting(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }
    private fun step() = compose.onNodeWithTag("wiz-step", useUnmergedTree = true)

    private fun openCurveWizardFromEmpty() {
        waitTag("panel-empty-add"); tap("panel-empty-add")
        waitTag("wiz-kind-curve"); tap("wiz-kind-curve")
        waitTag("wiz-series-add")
    }

    private fun addSeries(name: String, address: String, scale: String, decimals: String) {
        tap("wiz-series-add")
        waitTag("wiz-sheet")
        type("wiz-series-name", name)
        tap("wiz-series-point")
        waitTag("wiz-point-done")
        type("wiz-address", address)
        type("wiz-scale", scale)
        type("wiz-decimals", decimals)
        tap("wiz-point-done")
        waitTag("wiz-series-done")
        tap("wiz-series-done")
        waitGone("wiz-sheet")
    }

    @Test fun emptyPanelAddCurveWithTwoSeriesThenSaveWritesFilePollsAndRecordsHistoryInMemory() {
        val repo = repoWith(emptyProject())
        val before = File(dir, "default.json").readText()
        show(repo)
        openCurveWizardFromEmpty()
        step().assertTextContains("2 / 3", substring = true)
        addSeries("供水温度", "100", "0.1", "1")
        addSeries("供水压力", "101", "0.01", "2")
        // 向导里的预览曲线：两个系列都读到值
        waitTag("wiz-series-row-1")
        compose.waitUntil(8_000) {
            compose.onAllNodes(tagStarting("curve-chart-"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() && count("curve-waiting") == 0
        }
        assertEquals("向导期间不写盘", before, File(dir, "default.json").readText())
        tap("wiz-next")
        waitTag("wiz-look-preview")
        step().assertTextContains("3 / 3", substring = true)
        tap("wiz-next")
        waitTag("edit-grid")
        compose.onAllNodes(tagStarting("edit-card-"), useUnmergedTree = true).assertCountEquals(1)
        assertEquals("加入草稿但完成前不写盘", 0, reloaded().panels[0].widgets.size)
        tap("edit-done")
        waitTag("panel-grid")

        val saved = reloaded().panels[0].widgets.single()
        assertEquals("curve", saved.kind)
        assertEquals(2, saved.spanColumns)
        val curve = saved.curve!!
        assertEquals(listOf("供水温度", "供水压力"), curve.series.map { it.point.name })
        assertEquals(listOf(100, 101), curve.series.map { it.point.address })
        assertEquals(listOf(0.1, 0.01), curve.series.map { it.point.conversion.scale })
        assertEquals(60, curve.windowSec)
        assertEquals(2, curve.series.map { it.point.id }.toSet().size)
        assertEquals("两个系列颜色不同", 2, curve.series.map { it.colorIndex }.toSet().size)
        assertTrue("文件里没有样本，只有配置", !File(dir, "default.json").readText().contains("\"samples\""))

        // 面板上出现曲线卡，轮询把样本记进内存历史
        waitTag("curve-chart-${saved.id}")
        compose.waitUntil(8_000) { curve.series.all { history.count(it.point.id) >= 2 } }
        val temp = history.read(curve.series[0].point.id)
        assertEquals("换算后的工程值 23.4", 23.4, temp.values.last(), 1e-9)
        assertEquals(0.82, history.read(curve.series[1].point.id).values.last(), 1e-9)
        compose.waitUntil(8_000) { count("curve-waiting") == 0 }
        assertTrue("整个过程只读不写", session.requests.none { it.isWrite })
    }

    @Test fun nextWithoutAnySeriesStaysAndExplains() {
        val repo = repoWith(emptyProject())
        show(repo)
        openCurveWizardFromEmpty()
        tap("wiz-next")
        step().assertTextContains("2 / 3", substring = true)
        compose.onAllNodesWithText("至少", substring = true, useUnmergedTree = true).onFirst().assertExists()
        assertEquals(1, count("wiz-series-empty"))
    }

    @Test fun editingAnExistingCurveKeepsIdsAndCanDropASeries() {
        val a = PointDefinition("pa", "供水温度", 1, RegisterArea.HOLDING_REGISTER, 100, DataType.S16, conversion = Conversion(0.1, 0.0, 1), unitLabel = "℃", pollMs = 200)
        val b = PointDefinition("pb", "供水压力", 1, RegisterArea.HOLDING_REGISTER, 101, DataType.U16, conversion = Conversion(0.01, 0.0, 2), unitLabel = "MPa", pollMs = 200)
        val curve = Widget(
            "w-c", WidgetKinds.CURVE, spanColumns = 2,
            curve = CurveConfig(listOf(CurveSeries(a, 0), CurveSeries(b, 1, axis = YAxis.RIGHT)), windowSec = 300),
        )
        val repo = repoWith(Project("default", "x", listOf(Panel("p1", "机房 A", widgets = listOf(curve)))))
        show(repo)
        waitTag("curve-chart-w-c")
        waitText("5 分钟")
        tap("panel-edit"); waitTag("edit-grid")
        assertEquals("编辑期间不画实时图", 0, count("curve-chart-w-c"))
        tap("edit-card-w-c"); tap("edit-configure")
        waitTag("wiz-series-row-1")
        step().assertTextContains("2 / 3", substring = true)
        compose.onNodeWithTag("wiz-series-sub-1", useUnmergedTree = true).assertTextContains("HR101", substring = true)
        tap("wiz-series-row-1")
        waitTag("wiz-series-delete")
        tap("wiz-series-delete")
        waitGone("wiz-sheet")
        assertEquals(0, count("wiz-series-row-1"))
        tap("wiz-next"); waitTag("wiz-look-preview")
        tap("wiz-next"); waitTag("edit-grid")
        tap("edit-done"); waitTag("panel-grid")
        val saved = reloaded().panels[0].widgets.single()
        assertEquals("w-c", saved.id)
        assertEquals(listOf("pa"), saved.curve!!.series.map { it.point.id })
        assertEquals(300, saved.curve!!.windowSec)
        assertTrue("删掉的系列不再轮询、不再留历史", !history.isTracked("pb"))
        assertTrue(session.requests.none { it.isWrite })
    }

    @Test fun cancellingTheCurveWizardLeavesFileAndPanelUntouched() {
        val repo = repoWith(emptyProject())
        val before = File(dir, "default.json").readText()
        show(repo)
        openCurveWizardFromEmpty()
        addSeries("供水温度", "100", "0.1", "1")
        tap("sub-back")                       // 第 2 步按返回 → 第 1 步
        waitTag("wiz-kind-curve")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitTag("edit-empty")
        tap("edit-done")
        waitTag("panel-empty-add")
        assertEquals(before, File(dir, "default.json").readText())
        assertEquals(0, history.count("preview-0"))
    }
}
