package io.github.xingyingyuzhui.modbusmobile.application.export

import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectJson
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectLoad
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectSave
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.application.slave.FileSlaveSetupStore
import io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveRegion
import io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetup
import io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetupJson
import io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveUnitSetup
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json 在 JVM 单测里是 Android 的桩，必须用 Robolectric 才有真实实现。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArchiveImportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val utc = TimeZone.getTimeZone("UTC")

    private fun point(id: String) = PointDefinition(id = id, name = "温度$id", unitId = 1, area = RegisterArea.HOLDING_REGISTER, address = 10, type = DataType.U16)

    private fun project(name: String = "车间 A") = Project(
        id = "default",
        name = name,
        panels = listOf(
            Panel(
                "pn1", "主面板",
                widgets = listOf(
                    Widget("w1", WidgetKinds.NUMBER, "进水", point = point("p1")),
                    Widget("w2", WidgetKinds.NUMBER, "出水", point = point("p2")),
                ),
            ),
        ),
    )

    private fun slave() = SlaveSetup(
        units = listOf(
            SlaveUnitSetup(1, listOf(SlaveRegion(RegisterArea.HOLDING_REGISTER, 0, IntArray(4).toList()))),
            SlaveUnitSetup(2, listOf(SlaveRegion(RegisterArea.COIL, 0, listOf(1, 0)))),
        ),
    )

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray, limits: ImportLimits = ImportLimits()) = readArchiveImport(ByteArrayInputStream(bytes), limits)

    @Test
    fun ownExportRoundTripsIntoAPreview() {
        val input = ArchiveInput(
            zone = utc,
            projectJson = ProjectJson.encode(project()),
            slaveJson = SlaveSetupJson.encode(slave()),
            points = pointsForExport(project()),
        )
        val bytes = archiveBytes(setOf(ArchivePart.PROJECT, ArchivePart.SLAVE, ArchivePart.POINTS, ArchivePart.HISTORY), input)
        val preview = read(bytes) as ImportRead.Preview
        val p = preview.project!!
        assertEquals("车间 A", p.name)
        assertEquals(1, p.panels)
        assertEquals(2, p.widgets)
        assertEquals(2, p.points)
        assertEquals(input.projectJson, p.text)
        assertEquals(listOf(1, 2), preview.slave!!.unitIds)
        assertEquals(2, preview.slave!!.regions)
    }

    @Test
    fun configsAfterOtherFilesAreFoundAndTheOthersAreListedAsSkipped() {
        val bytes = zip(
            ArchiveNames.POINTS to "id\r\n".toByteArray(),
            ArchiveNames.SLAVE to SlaveSetupJson.encode(slave()).toByteArray(),
        )
        val preview = read(bytes) as ImportRead.Preview
        assertEquals(null, preview.project)
        assertEquals(listOf(ArchiveNames.POINTS), preview.skipped)
    }

    @Test
    fun stopsReadingOnceBothConfigsAreFound() {
        // 本应用导出先写配置；后面巨大的历史不用解压。上限小于历史的大小也能成功。
        val history = ByteArray(2 * 1024 * 1024).also { Random(7).nextBytes(it) }
        val bytes = zip(
            ArchiveNames.PROJECT to ProjectJson.encode(project()).toByteArray(),
            ArchiveNames.SLAVE to SlaveSetupJson.encode(slave()).toByteArray(),
            ArchiveNames.HISTORY to history,
        )
        assertTrue(read(bytes, ImportLimits(maxArchiveBytes = 512L * 1024)) is ImportRead.Preview)
    }

    @Test
    fun archiveCapStopsAHugeFileBeforeTheConfigs() {
        val noise = ByteArray(2 * 1024 * 1024).also { Random(9).nextBytes(it) }
        val bytes = zip(ArchiveNames.HISTORY to noise, ArchiveNames.PROJECT to ProjectJson.encode(project()).toByteArray())
        val failed = read(bytes, ImportLimits(maxArchiveBytes = 512L * 1024)) as ImportRead.Failed
        assertTrue(failed.reason, failed.reason.contains("超过"))
    }

    @Test
    fun oversizedConfigEntryIsRejected() {
        // 高度可压缩：压缩后很小，解压后超过上限（压缩炸弹的形状）。
        val padded = "{\"version\":1," + " ".repeat(64 * 1024) + "}"
        val failed = read(zip(ArchiveNames.PROJECT to padded.toByteArray()), ImportLimits(maxEntryBytes = 16 * 1024)) as ImportRead.Failed
        assertTrue(failed.reason, failed.reason.contains(ArchiveNames.PROJECT))
    }

    @Test
    fun notAZipNoConfigsAndNestedNamesFail() {
        assertTrue(read("hello".toByteArray()) is ImportRead.Failed)
        assertTrue(read(zip(ArchiveNames.POINTS to "x".toByteArray())) is ImportRead.Failed)
        // 只认根目录：解压后再打包多出一层目录的不算。
        assertTrue(read(zip("export/" + ArchiveNames.PROJECT to ProjectJson.encode(project()).toByteArray())) is ImportRead.Failed)
    }

    @Test
    fun badJsonAndNewerVersionsAreRejectedWithAReason() {
        val bad = read(zip(ArchiveNames.PROJECT to "{not json".toByteArray())) as ImportRead.Failed
        assertTrue(bad.reason, bad.reason.startsWith("工程配置格式错误"))
        val newer = JSONObject(ProjectJson.encode(project())).put("version", Project.FORMAT_VERSION + 1).toString()
        val rejected = read(zip(ArchiveNames.PROJECT to newer.toByteArray())) as ImportRead.Failed
        assertTrue(rejected.reason, rejected.reason.contains("更新版本"))
        val newerSlave = JSONObject(SlaveSetupJson.encode(slave())).put("version", SlaveSetupJson.FORMAT_VERSION + 1).toString()
        assertTrue(read(zip(ArchiveNames.SLAVE to newerSlave.toByteArray())) is ImportRead.Failed)
    }

    @Test
    fun byteOrderMarkIsAccepted() {
        val text = "\uFEFF" + ProjectJson.encode(project())
        val preview = read(zip(ArchiveNames.PROJECT to text.toByteArray())) as ImportRead.Preview
        assertEquals("车间 A", preview.project!!.name)
    }

    @Test
    fun repositoryImportKeepsTheOldFileAndWritesTheOriginalText() {
        val dir = tmp.newFolder("projects")
        val repo = ProjectRepository(dir, clock = { 1234L })
        assertEquals(ProjectSave.Saved, repo.save(project("旧工程")))
        // 未知组件：这个版本读不懂，原文要逐字写进去。
        val incoming = JSONObject(ProjectJson.encode(project("新工程"))).apply {
            getJSONArray("panels").getJSONObject(0).getJSONArray("widgets")
                .put(JSONObject().put("id", "w9").put("kind", "hologram").put("extra", "keep me"))
        }.toString()
        assertEquals(0, repo.revision.value)
        assertEquals(ProjectSave.Saved, repo.importJson(incoming))
        assertEquals(1, repo.revision.value)
        assertEquals(incoming, File(dir, "default.json").readText())
        assertTrue(File(dir, "default.json.before-import-1234").readText().contains("旧工程"))
        val loaded = repo.load() as ProjectLoad.Loaded
        assertEquals("新工程", loaded.project.name)
    }

    @Test
    fun repositoryImportRejectsBadAndNewerTextWithoutTouchingTheFile() {
        val dir = tmp.newFolder("projects")
        val repo = ProjectRepository(dir)
        repo.save(project("旧工程"))
        val before = File(dir, "default.json").readText()
        assertTrue(repo.importJson("{bad") is ProjectSave.Failed)
        val newer = JSONObject(ProjectJson.encode(project())).put("version", Project.FORMAT_VERSION + 1).toString()
        assertEquals(ProjectSave.ReadOnly, repo.importJson(newer))
        assertEquals(before, File(dir, "default.json").readText())
        assertEquals(0, repo.revision.value)
        assertFalse(dir.list()!!.any { it.contains("before-import") })
    }

    @Test
    fun slaveStoreImportReplacesTheSavedSetupAndKeepsTheOldOne() {
        val dir = tmp.newFolder("projects")
        val store = FileSlaveSetupStore(dir, clock = { 99L })
        store.save(SlaveSetup.default())
        assertTrue(store.importJson(SlaveSetupJson.encode(slave())))
        assertEquals(listOf(1, 2), store.load().unitIds)
        assertTrue(File(dir, "slave.json.before-import-99").exists())
        assertFalse(store.importJson("{bad"))
        assertEquals(listOf(1, 2), store.load().unitIds)
    }

    @Test
    fun importerRefusesWhileASessionRunsAndReportsPartialFailure() {
        val dir = tmp.newFolder("projects")
        val repo = ProjectRepository(dir)
        var state: SessionState = SessionState.Running(Role.SLAVE, io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget.Demo, 1)
        val failingSlaves = object : io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetupStore {
            override fun load() = SlaveSetup.default()
            override fun save(setup: SlaveSetup) = false
        }
        val importer = ArchiveImporter(repo, failingSlaves) { state }
        val preview = ImportRead.Preview(
            project = parseProject(ProjectJson.encode(project("导入的"))).getOrThrow(),
            slave = parseSlave(SlaveSetupJson.encode(slave())).getOrThrow(),
            skipped = emptyList(),
        )
        assertEquals(ImportApply.Blocked, importer.apply(preview, setOf(ImportPart.PROJECT, ImportPart.SLAVE)))
        assertFalse(File(dir, "default.json").exists())

        state = SessionState.Idle
        val result = importer.apply(preview, setOf(ImportPart.PROJECT, ImportPart.SLAVE)) as ImportApply.Failed
        assertTrue(result.projectDone)
        assertEquals("导入的", (repo.load() as ProjectLoad.Loaded).project.name)

        // 只勾工程时不碰从站。
        assertEquals(ImportApply.Done(project = true, slave = false), importer.apply(preview, setOf(ImportPart.PROJECT)))
    }
}
