package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.Conversion
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveConfig
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveSeries
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PanelRuntime
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointPreview
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointStore
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollScheduler
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository
import io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.shell.DockTag
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 曲线全屏（P8a）端到端：真导航 + 真 ViewModel + 真工程文件 + 真轮询 / 样本历史。
 * 覆盖：⤢ 进入 / 轮询不中断 / 关闭回面板；横屏自动进入（只在面板有曲线时）；手动关闭后不再自动进；快照的隐私提示（取消 = 什么都不写）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelFullscreenFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private class Session : PanelFakeSession(Role.MASTER) {
        val requests = CopyOnWriteArrayList<ModbusRequest>()
        override suspend fun execute(request: ModbusRequest): ModbusResult {
            requests += request
            val r = request as? ModbusRequest.ReadHoldingRegisters
                ?: return ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(0)))
            val values = (0 until r.quantity).map { i -> when (r.address + i) { 100 -> 234; 101 -> 82; else -> 0 } }
            return ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, values))
        }
    }

    private val session = Session()
    private val dir get() = File(tmp.root, "projects")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val history = SampleHistory()

    @After fun tearDown() { scope.cancel() }

    private fun pt(id: String, name: String, address: Int, scale: Double, decimals: Int, unit: String) = PointDefinition(
        id, name, 1, RegisterArea.HOLDING_REGISTER, address, DataType.U16,
        conversion = Conversion(scale, 0.0, decimals), unitLabel = unit, pollMs = 200, rangeMin = 0.0, rangeMax = 50.0,
    )

    private val temp = pt("t1", "供水温度", 100, 0.1, 1, "℃")
    private val press = pt("t2", "供水压力", 101, 0.01, 2, "MPa")
    private val curve = Widget(
        "wc", WidgetKinds.CURVE, spanColumns = 2, spanRows = 2,
        curve = CurveConfig(listOf(CurveSeries(temp, 0), CurveSeries(press, 1)), windowSec = 60),
    )
    private val gauge = Widget("wg", WidgetKinds.GAUGE, point = temp.copy(id = "g1"))

    private fun repoWith(vararg widgets: Widget): ProjectRepository =
        ProjectRepository(dir).also { it.save(Project("default", "x", listOf(Panel("p1", "机房 A", widgets = widgets.toList())))) }

    private fun runtime(): PanelRuntime {
        val store = PointStore()
        val clock = { System.nanoTime() / 1_000_000 }
        return PanelRuntime(store, PollScheduler(session, scope, store, nowMs = clock, history = history), history) { s -> PointPreview(session, s, clock) }
    }

    private fun show(repository: ProjectRepository, landscapeCurve: Boolean = false) {
        val settings = UiSettings(theme = ThemeMode.DARK, landscapeCurveFullscreen = landscapeCurve)
        val rt = runtime()
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides MemorySettingsRepository(settings),
                LocalProjectRepository provides repository,
                LocalPanelRuntime provides rt,
            ) { ModbusTheme(settings) { ModbusApp(startRoute = Destination.Panel.route) } }
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun waitTag(tag: String, ms: Long = 8_000) = compose.waitUntil(ms) { count(tag) > 0 }
    private fun waitGone(tag: String, ms: Long = 8_000) = compose.waitUntil(ms) { count(tag) == 0 }
    private fun tap(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
    private fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty()

    // ---- 竖屏：⤢ ----

    @Test fun expandOpensFullscreenKeepsPollingAndCloseReturnsToThePanel() {
        show(repoWith(curve, gauge))
        waitTag("curve-expand-wc")
        compose.waitUntil(8_000) { history.count("t1") >= 2 }
        tap("curve-expand-wc")
        waitTag("curve-fs")
        assertEquals("全屏页隐藏 dock", 0, count(DockTag))
        compose.onNodeWithTag("curve-fs-title", useUnmergedTree = true).assertTextEquals("供水温度 / 供水压力")
        waitTag("curve-fs-chart")
        compose.waitUntil(8_000) { text("curve-fs-status") == "轮询中" }
        // 面板页离开（onStop）后曲线页接着轮询：样本继续涨，不是停在进入那一刻
        val before = history.count("t1")
        compose.waitUntil(8_000) { history.count("t1") >= before + 3 }
        compose.waitUntil(8_000) { text("curve-fs-value-0") == "23.4" }
        assertEquals("0.82", text("curve-fs-value-1"))
        // 点位读数和面板一致
        tap("curve-fs-close")
        waitTag("panel-grid")
        waitGone("curve-fs")
        assertEquals(1, count(DockTag))
        compose.waitUntil(8_000) { text("panel-value-wg") == "23.4" }
        val afterClose = history.count("t1")
        compose.waitUntil(8_000) { history.count("t1") >= afterClose + 3 }
    }

    @Test fun gaugeCardShowsTheLiveValueFromTheRealPoll() {
        show(repoWith(gauge))
        waitTag("panel-gauge-wg")
        compose.waitUntil(8_000) { text("panel-value-wg") == "23.4" }
        assertEquals("面板没有曲线就没有 ⤢", 0, count("curve-expand-wc"))
    }

    // ---- 横屏自动进入 ----

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeAutoEntersFullscreenOnlyWhenThePanelHasACurveAndStaysClosedAfterClosing() {
        show(repoWith(curve, gauge), landscapeCurve = true)
        waitTag("curve-fs")
        compose.onNodeWithTag("curve-fs-title", useUnmergedTree = true).assertTextEquals("供水温度 / 供水压力")
        tap("curve-fs-close")
        waitTag("panel-grid")
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
        assertEquals("手动关闭后（仍是横屏）不再自动进入", 0, count("curve-fs"))
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeWithoutACurveInThePanelStaysOnThePanel() {
        show(repoWith(gauge), landscapeCurve = true)
        waitTag("panel-grid")
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
        assertEquals("没有曲线不进空页", 0, count("curve-fs"))
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeSettingOffNeverAutoEntersButTheExpandIconStillWorks() {
        show(repoWith(curve), landscapeCurve = false)
        waitTag("curve-expand-wc")
        Thread.sleep(300)
        compose.waitForIdle()
        assertEquals(0, count("curve-fs"))
        tap("curve-expand-wc")
        waitTag("curve-fs")
    }

    // ---- 快照：隐私提示 ----

    private fun exports() = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "exports")

    @Test fun snapshotAsksPrivacyFirstCancelWritesNothingContinueThenShareWritesAPng() {
        show(repoWith(curve))
        waitTag("curve-expand-wc")
        tap("curve-expand-wc")
        waitTag("curve-fs-snapshot")
        compose.waitUntil(8_000) { history.count("t1") >= 3 }
        // Robolectric 里没有真正的帧循环：先截一次屏让页面画一遍，图层才有尺寸（真机上每帧都在画）
        compose.onRoot().captureToImage()
        tap("curve-fs-snapshot")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("保存前请注意").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("取消").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("保存前请注意").fetchSemanticsNodes().isEmpty() }
        assertTrue("取消 = 什么都不写", exports().listFiles().orEmpty().isEmpty())
        tap("curve-fs-snapshot")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("保存前请注意").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("继续").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("保存曲线快照").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("分享…").performClick()
        // 文件一创建就能列出来，但 PNG 还在写：等写完（有 IEND 结尾）再检查
        compose.waitUntil(8_000) { exports().listFiles().orEmpty().singleOrNull()?.let { it.length() > 2_000 && it.readBytes().takeLast(8).take(4) == listOf<Byte>(0x49, 0x45, 0x4E, 0x44) } == true }
        val png = exports().listFiles()!!.single()
        assertTrue(png.name, png.name.startsWith("modbus-curve-") && png.name.endsWith(".png"))
        val head = png.inputStream().use { it.readNBytes(8) }
        assertEquals("是真 PNG", listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), head.map { it.toInt() and 0xFF })
        assertTrue("图不是空的 ${png.length()}", png.length() > 2_000)
        compose.waitUntil(8_000) { shadowOf(compose.activity).peekNextStartedActivity() != null }
        val started = shadowOf(compose.activity).nextStartedActivity
        assertTrue("拉起系统分享", started?.action == android.content.Intent.ACTION_CHOOSER)
    }
}
