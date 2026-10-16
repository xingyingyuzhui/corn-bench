package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.application.panel.Conversion
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.ErrorKind
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PanelRuntime
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointError
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointStore
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollScheduler
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository
import io.github.xingyingyuzhui.modbusmobile.application.panel.Quality
import io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelContent
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 面板页（P1）：没有示例卡；空工程显示诚实的空状态；工程文件读不了时给出提示且不动原文件。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelPageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private class Session : SessionManager {
        override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, epoch = 1))
        override val traffic = MutableSharedFlow<TrafficEvent>(replay = 1)
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(SerialSettings())
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        @Volatile var requests = 0
        @Volatile var handler: (ModbusRequest) -> ModbusResult = { ModbusResult.Disconnected }
        override suspend fun execute(request: ModbusRequest): ModbusResult {
            requests++
            return handler(request)
        }
    }

    private val session = Session()

    private fun show(repository: ProjectRepository?, runtime: PanelRuntime? = null) {
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

    private fun waitTag(tag: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithTagCount(tag) > 0
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test fun emptyProjectShowsHonestEmptyStateAndNoSampleCards() {
        show(ProjectRepository(File(tmp.root, "projects")))
        waitTag("panel-empty")
        compose.onNodeWithTag("panel-empty").assertIsDisplayed()
        compose.onNodeWithText("还没有组件").assertIsDisplayed()
        for (gone in listOf("示例", "26.4", "压缩机", "阀门", "1.20")) {
            assertEquals(gone, 0, compose.onAllNodesWithText(gone, substring = true).fetchSemanticsNodes().size)
        }
    }

    @Test fun withoutRepositoryTheEmptyStateStillShows() {
        show(null)
        waitTag("panel-empty")
    }

    @Test fun corruptFileIsExplainedAndKept() {
        val dir = File(tmp.root, "projects").apply { mkdirs() }
        File(dir, "default.json").writeText("坏掉的内容")
        show(ProjectRepository(dir))
        waitTag("panel-corrupt")
        compose.onNodeWithText("工程文件无法读取").assertIsDisplayed()
        compose.onNodeWithText("没有删除任何内容", substring = true).assertIsDisplayed()
        assertEquals("坏掉的内容", File(dir, "default.json").readText())
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeEmptyStateShowsToo() {
        show(ProjectRepository(File(tmp.root, "projects")))
        waitTag("panel-empty")
        compose.onNodeWithTag("panel-empty").assertIsDisplayed()
        compose.onNodeWithText("还没有组件").assertIsDisplayed()
    }

    private fun point(id: String, type: DataType = DataType.S16, unit: String = "°C") = PointDefinition(
        id = id, name = "温度 $id", unitId = 1, area = RegisterArea.HOLDING_REGISTER, address = 0, type = type,
        conversion = Conversion(0.1, -20.0, 1), unitLabel = unit, pollMs = 100,
    )

    private fun projectWith(vararg points: PointDefinition) = Project(
        "default", "x",
        listOf(Panel("p", "面板", widgets = points.mapIndexed { i, p -> Widget("w${i + 1}", WidgetKinds.NUMBER, "", p) })),
    )

    @Test fun configuredPointsPollAndShowRealValuesWithPollStats() {
        val repo = ProjectRepository(File(tmp.root, "projects"))
        repo.save(projectWith(point("a")))
        session.handler = {
            ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(1000)))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = PointStore()
            val runtime = PanelRuntime(store, PollScheduler(session, scope, store, nowMs = { System.nanoTime() / 1_000_000 }))
            show(repo, runtime)
            compose.waitUntil(8_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasTestTag("panel-value-w1"), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty() && store.get("a")?.quality == Quality.OK
            }
            compose.onNodeWithTag("panel-value-w1", useUnmergedTree = true).assertTextEquals("80.0")
            compose.onNodeWithText("温度 a", useUnmergedTree = true).assertIsDisplayed()
            compose.waitUntil(8_000) { session.requests >= 3 }
            compose.onNodeWithText("设定 100 ms", substring = true, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("主站 · 轮询中", useUnmergedTree = true).assertIsDisplayed()
        } finally {
            scope.cancel()
        }
    }
}
