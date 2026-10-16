package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** org.json 在 JVM 单测里是 Android 的桩，必须用 Robolectric 才有真实实现。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProjectRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun point(id: String = "p1", type: DataType = DataType.S16) = PointDefinition(
        id = id, name = "温度", unitId = 3, area = RegisterArea.INPUT_REGISTER, address = 100, type = type,
        order = ByteWordOrder.CDAB, bit = 2, asciiRegisters = 4,
        conversion = Conversion(0.1, -20.0, 1), unitLabel = "°C", rangeMin = -20.0, rangeMax = 120.0, pollMs = 500,
        thresholds = listOf(
            ThresholdBand(ThresholdLevel.WARN, 60.0, 80.0),
            ThresholdBand(ThresholdLevel.ALERT, 80.0, null),
            ThresholdBand(ThresholdLevel.ALERT, null, 0.0),
        ),
    )

    private fun project() = Project(
        id = "default", name = "车间 A",
        panels = listOf(
            Panel(
                "pn1", "主面板", columns = 3,
                widgets = listOf(
                    Widget("w1", WidgetKinds.NUMBER, "进水温度", point(), 2, 1),
                    Widget("w2", WidgetKinds.LAMP, "压缩机", point("p2", DataType.U16)),
                ),
            ),
        ),
    )

    private fun repo(dir: File = tmp.root) = ProjectRepository(File(dir, "projects"), clock = { 42L })

    private fun mainFile() = File(tmp.root, "projects/default.json")

    @Test fun missingFileGivesEmptyProject() {
        val r = repo().load() as ProjectLoad.Loaded
        assertEquals(Project.empty(), r.project)
        assertTrue(r.warnings.isEmpty())
        assertFalse(mainFile().exists())
    }

    @Test fun roundTripKeepsEverything() {
        val repo = repo()
        assertEquals(ProjectSave.Saved, repo.save(project()))
        val loaded = repo.load() as ProjectLoad.Loaded
        assertEquals(project(), loaded.project)
        assertTrue(loaded.warnings.isEmpty())
        assertFalse(loaded.readOnly)
        assertTrue(loaded.project.panels[0].widgets.all { it.isUsable })
    }

    @Test fun secondSaveKeepsPreviousVersionAsBackupAndLeavesNoTemp() {
        val repo = repo()
        repo.save(project())
        val renamed = project().copy(name = "车间 B")
        repo.save(renamed)
        assertEquals("车间 B", (repo.load() as ProjectLoad.Loaded).project.name)
        val bak = File(tmp.root, "projects/default.json.bak")
        assertEquals("车间 A", ProjectJson.decode(bak.readText()).project.name)
        assertFalse(File(tmp.root, "projects/default.json.tmp").exists())
    }

    @Test fun corruptMainFallsBackToBackup() {
        val repo = repo()
        repo.save(project())
        repo.save(project().copy(name = "新"))
        mainFile().writeText("{ 半截 json")
        val r = repo.load() as ProjectLoad.Loaded
        assertTrue(r.fromBackup)
        assertEquals("车间 A", r.project.name)
        assertTrue(r.warnings.any { "备份" in it })
    }

    @Test fun corruptWithoutBackupIsReportedAndOriginalKept() {
        val repo = repo()
        mainFile().parentFile!!.mkdirs()
        mainFile().writeText("not json at all")
        val r = repo.load() as ProjectLoad.Corrupt
        assertNotNull(r.corruptCopy)
        assertEquals("not json at all", r.corruptCopy!!.readText())
        assertEquals("not json at all", mainFile().readText()) // 原文件没动
    }

    @Test fun missingVersionIsCorrupt() {
        mainFile().parentFile!!.mkdirs()
        mainFile().writeText("""{"id":"default","panels":[]}""")
        assertTrue(repo().load() is ProjectLoad.Corrupt)
    }

    @Test fun newerVersionIsReadOnlyAndNeverOverwritten() {
        val repo = repo()
        val text = JSONObject(ProjectJson.encode(project())).put("version", 99).put("futureField", "x").toString()
        mainFile().parentFile!!.mkdirs()
        mainFile().writeText(text)
        val r = repo.load() as ProjectLoad.Loaded
        assertTrue(r.readOnly)
        assertTrue(r.warnings.any { "只读" in it })
        assertEquals("车间 A", r.project.name)
        assertEquals(ProjectSave.ReadOnly, repo.save(r.project))
        assertEquals(text, mainFile().readText())
    }

    @Test fun unknownWidgetKindIsPreservedVerbatim() {
        val obj = JSONObject(ProjectJson.encode(project()))
        val future = JSONObject("""{"id":"wx","kind":"gauge3d","title":"未来组件","extra":{"a":[1,2,3]}}""")
        obj.getJSONArray("panels").getJSONObject(0).getJSONArray("widgets").put(future)
        mainFile().parentFile!!.mkdirs()
        mainFile().writeText(obj.toString())
        val repo = repo()
        val r = repo.load() as ProjectLoad.Loaded
        val widgets = r.project.panels[0].widgets
        assertEquals(3, widgets.size)
        assertFalse(widgets[2].isUsable)
        assertTrue(r.warnings.any { "gauge3d" in it })
        assertEquals(2, r.project.usablePoints.size)
        // 保存再读：未知组件的内容一字不差
        repo.save(r.project)
        val again = JSONObject(mainFile().readText()).getJSONArray("panels").getJSONObject(0).getJSONArray("widgets").getJSONObject(2)
        assertEquals(future.toString(), again.toString())
    }

    @Test fun invalidPointKeepsWidgetRawInsteadOfDroppingOrCrashing() {
        val obj = JSONObject(ProjectJson.encode(project()))
        val bad = obj.getJSONArray("panels").getJSONObject(0).getJSONArray("widgets").getJSONObject(0)
        bad.getJSONObject("point").put("scale", 0.0)
        mainFile().parentFile!!.mkdirs()
        mainFile().writeText(obj.toString())
        val r = repo().load() as ProjectLoad.Loaded
        assertEquals(2, r.project.panels[0].widgets.size)
        assertFalse(r.project.panels[0].widgets[0].isUsable)
        assertTrue(r.project.panels[0].widgets[1].isUsable)
        assertTrue(r.warnings.any { "BAD_CONVERSION" in it })
    }

    @Test fun badEnumAndMissingFieldsAreTolerated() {
        val obj = JSONObject(ProjectJson.encode(project()))
        val widgets = obj.getJSONArray("panels").getJSONObject(0).getJSONArray("widgets")
        widgets.getJSONObject(0).getJSONObject("point").put("type", "COMPLEX128")
        widgets.getJSONObject(1).getJSONObject("point").remove("address")
        widgets.put("not an object")
        mainFile().parentFile!!.mkdirs()
        mainFile().writeText(obj.toString())
        val r = repo().load() as ProjectLoad.Loaded
        assertEquals(2, r.project.panels[0].widgets.size)
        assertTrue(r.project.panels[0].widgets.none { it.isUsable })
        assertTrue(r.warnings.size >= 3)
    }

    @Test fun pollPeriodRoundTripsDefaultsAndRejectsOutOfRange() {
        val obj = JSONObject(ProjectJson.encode(project()))
        val widgets = obj.getJSONArray("panels").getJSONObject(0).getJSONArray("widgets")
        assertEquals(500, widgets.getJSONObject(0).getJSONObject("point").getInt("pollMs"))
        widgets.getJSONObject(1).getJSONObject("point").remove("pollMs") // 老文件没有这个字段
        widgets.getJSONObject(0).getJSONObject("point").put("pollMs", 50)
        mainFile().parentFile!!.mkdirs()
        mainFile().writeText(obj.toString())
        val r = repo().load() as ProjectLoad.Loaded
        assertFalse(r.project.panels[0].widgets[0].isUsable)
        assertTrue(r.warnings.any { "POLL_RANGE" in it })
        assertEquals(PointDefinition.DEFAULT_POLL_MS, r.project.panels[0].widgets[1].point!!.pollMs)
    }

    @Test fun columnsAndSpansAreClamped() {
        val obj = JSONObject(ProjectJson.encode(project()))
        val panel = obj.getJSONArray("panels").getJSONObject(0)
        panel.put("columns", 9)
        panel.getJSONArray("widgets").getJSONObject(0).put("spanColumns", 7).put("spanRows", 0)
        mainFile().parentFile!!.mkdirs()
        mainFile().writeText(obj.toString())
        val loaded = (repo().load() as ProjectLoad.Loaded).project.panels[0]
        assertEquals(4, loaded.columns)
        assertEquals(2, loaded.widgets[0].spanColumns)
        assertEquals(1, loaded.widgets[0].spanRows)
    }

    @Test fun saveToUnwritableDirectoryReportsFailureWithoutThrowing() {
        val blocker = tmp.newFile("blocker")
        val repo = ProjectRepository(File(blocker, "sub"))
        assertTrue(repo.save(project()) is ProjectSave.Failed)
    }

    @Test fun optionalNumbersStayNullWhenAbsent() {
        val p = point().copy(conversion = Conversion(1.0, 0.0, null), rangeMin = null, rangeMax = null, thresholds = emptyList())
        val repo = repo()
        repo.save(Project("default", "x", listOf(Panel("a", "b", widgets = listOf(Widget("w", WidgetKinds.NUMBER, "t", p))))))
        val back = (repo.load() as ProjectLoad.Loaded).project.panels[0].widgets[0].point!!
        assertNull(back.conversion.decimals)
        assertNull(back.rangeMin)
        assertNull(back.rangeMax)
        assertTrue(back.thresholds.isEmpty())
    }
}
