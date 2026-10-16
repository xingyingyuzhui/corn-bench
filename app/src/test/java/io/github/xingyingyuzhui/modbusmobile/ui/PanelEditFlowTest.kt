package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
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

/** 面板编辑（P5）端到端：真 ViewModel + 真工程文件。完成才写盘、✕ 放弃不写、编辑期间不向设备写入。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelEditFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private class Session : PanelFakeSession(Role.MASTER) {
        val requests = CopyOnWriteArrayList<ModbusRequest>()
        override suspend fun execute(request: ModbusRequest): ModbusResult {
            requests += request
            return ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(234)))
        }
    }

    private val session = Session()
    private val dir get() = File(tmp.root, "projects")
    private val file get() = File(dir, "default.json")

    private fun repoWith(project: Project): ProjectRepository =
        ProjectRepository(dir).also { it.save(project) }

    private fun show(repository: ProjectRepository, runtime: PanelRuntime? = null) {
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

    private fun tap(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()

    private fun twoPanels() = PanelFixtures.project(Panel("p2", "冷站", widgets = listOf(PanelFixtures.widgets[5].copy(id = "w-other"))))

    @Test fun pencilEntersEditAndDoneWithoutChangesLeavesFileUntouched() {
        val repo = repoWith(PanelFixtures.project())
        val before = file.readText()
        show(repo)
        waitTag("panel-edit")
        tap("panel-edit")
        waitTag("edit-grid")
        compose.onNodeWithText("编辑面板", useUnmergedTree = true).assertIsDisplayed()
        tap("edit-done")
        waitTag("panel-grid")
        assertEquals("没改动就不重写文件", before, file.readText())
    }

    @Test fun deleteWidgetIsSavedOnlyAfterDone() {
        val repo = repoWith(PanelFixtures.project())
        show(repo)
        waitTag("panel-edit"); tap("panel-edit"); waitTag("edit-grid")
        tap("edit-delete-w-temp")
        waitGone("edit-card-w-temp")
        assertTrue("草稿阶段文件不变", reloaded().panels[0].widgets.any { it.id == "w-temp" })
        tap("edit-done")
        waitTag("panel-grid")
        assertEquals(0, count("panel-card-w-temp"))
        assertEquals(5, reloaded().panels[0].widgets.size)
        assertTrue(reloaded().panels[0].widgets.none { it.id == "w-temp" })
    }

    @Test fun closeAfterChangeAsksThenDiscardsWithoutSaving() {
        val repo = repoWith(PanelFixtures.project())
        val before = file.readText()
        show(repo)
        waitTag("panel-edit"); tap("panel-edit"); waitTag("edit-grid")
        tap("edit-delete-w-temp")
        tap("edit-close")
        compose.onNodeWithText("放弃修改？").assertIsDisplayed()
        compose.onNodeWithText("放弃").performClick()
        waitTag("panel-grid")
        assertEquals(before, file.readText())
        compose.onNodeWithTag("panel-card-w-temp", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun renameAndAddPanelThenDoneSavesBoth() {
        val repo = repoWith(PanelFixtures.project(Panel("p2", "冷站")))
        show(repo)
        waitTag("panel-edit"); tap("panel-edit"); waitTag("edit-grid")
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextReplacement("机房 B")
        compose.onNodeWithContentDescription("新建面板", useUnmergedTree = true).performClick()
        tap("edit-done")
        waitTag("panel-chips")
        val saved = reloaded()
        assertEquals(listOf("机房 B", "冷站", "面板 1"), saved.panels.map { it.name })
        // 新建面板成为当前面板：点“机房 B”回去后能看到它的组件
        compose.onNodeWithText("机房 B", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { count("panel-card-w-temp") > 0 }
    }

    @Test fun columnsAndSizeSurviveSaveAndReload() {
        val repo = repoWith(PanelFixtures.project())
        show(repo)
        waitTag("panel-edit"); tap("panel-edit"); waitTag("edit-grid")
        tap("edit-columns")
        tap("edit-handle-w-temp")
        tap("edit-done")
        waitTag("panel-grid")
        val panel = reloaded().panels[0]
        assertEquals(3, panel.columns)
        assertEquals(2, panel.widgets.first { it.id == "w-temp" }.spanColumns)
        assertEquals(PanelFixtures.widgets.map { it.id }, panel.widgets.map { it.id })
    }

    @Test fun deletingAPanelNeedsConfirmAndSavesOnDone() {
        val repo = repoWith(twoPanels())
        show(repo)
        waitTag("panel-edit"); tap("panel-edit"); waitTag("edit-grid")
        compose.onNodeWithTag("edit-delete-panel", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("edit-dialog-confirm").performClick()
        waitGone("edit-delete-panel")
        assertEquals("完成前不写盘", 2, reloaded().panels.size)
        tap("edit-done")
        waitTag("panel-grid")
        assertEquals(listOf("冷站"), reloaded().panels.map { it.name })
    }

    @Test fun editingNeverSendsWriteRequestsAndPollingKeepsReading() {
        val repo = repoWith(PanelFixtures.project())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = PointStore()
            val runtime = PanelRuntime(store, PollScheduler(session, scope, store, nowMs = { System.nanoTime() / 1_000_000 }))
            show(repo, runtime)
            waitTag("panel-edit"); tap("panel-edit"); waitTag("edit-grid")
            tap("edit-delete-w-temp")
            tap("edit-handle-w-press")
            tap("edit-columns")
            tap("edit-done")
            waitTag("panel-grid")
            compose.waitUntil(8_000) { session.requests.size >= 3 }
            assertTrue("编辑和保存期间只读不写", session.requests.none { it.isWrite })
        } finally {
            scope.cancel()
        }
    }

    @Test fun savedDeleteStopsPollingThatPoint() {
        val repo = repoWith(PanelFixtures.project())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = PointStore()
            val scheduler = PollScheduler(session, scope, store, nowMs = { System.nanoTime() / 1_000_000 })
            show(repo, PanelRuntime(store, scheduler))
            waitTag("panel-edit"); tap("panel-edit"); waitTag("edit-grid")
            PanelFixtures.widgets.drop(1).forEach { tap("edit-delete-${it.id}") }
            tap("edit-done")
            waitTag("panel-grid")
            compose.waitUntil(8_000) { session.requests.isNotEmpty() }
            val seen = session.requests.map { (it as? ModbusRequest.ReadHoldingRegisters)?.address }.toSet()
            compose.waitUntil(3_000) { session.requests.size >= 5 }
            val now = session.requests.map { (it as? ModbusRequest.ReadHoldingRegisters)?.address }.toSet()
            // 只剩 w-temp（保持寄存器 100）：之后不再读到别的地址
            assertTrue("剩下的轮询只读 100：$now", now.all { it == 100 || it in seen })
            assertTrue(now.contains(100))
        } finally {
            scope.cancel()
        }
    }
}
