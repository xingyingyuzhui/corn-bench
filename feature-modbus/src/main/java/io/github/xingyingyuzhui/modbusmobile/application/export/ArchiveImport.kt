package io.github.xingyingyuzhui.modbusmobile.application.export

import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectJson
import io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetupJson
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * 从导出 ZIP 里读回配置：只认根目录的 `default.json`（面板工程）和 `slave.json`（从站配置）。
 * 点表、寄存器快照、历史和通信日志是派生数据，不导入，只在预览里列出被跳过的文件名。
 *
 * 读的时候设上限：ZIP 本身最多读 [ImportLimits.maxArchiveBytes]，每个配置文件解压后最多
 * [ImportLimits.maxEntryBytes]（防压缩炸弹）。两个配置都找到就停，不再解压后面的历史 CSV；
 * 本应用导出的 ZIP 总是先写配置。这里只读不写，真正替换由仓库的 `importJson` 做。
 */
data class ImportLimits(
    val maxArchiveBytes: Long = 256L * 1024 * 1024,
    val maxEntryBytes: Int = 4 * 1024 * 1024,
)

data class ProjectImport(
    val text: String,
    val name: String,
    val panels: Int,
    val widgets: Int,
    val points: Int,
    val warnings: List<String>,
)

data class SlaveImport(
    val text: String,
    val unitIds: List<Int>,
    val regions: Int,
    val rules: Int,
    val warnings: List<String>,
)

sealed interface ImportRead {
    /** [skipped]：ZIP 里不导入的其它文件。 */
    data class Preview(
        val project: ProjectImport?,
        val slave: SlaveImport?,
        val skipped: List<String>,
    ) : ImportRead

    data class Failed(val reason: String) : ImportRead
}

fun readArchiveImport(input: InputStream, limits: ImportLimits = ImportLimits()): ImportRead {
    val counted = CappedInput(input, limits.maxArchiveBytes)
    var projectText: String? = null
    var slaveText: String? = null
    val skipped = ArrayList<String>()
    var entries = 0
    try {
        ZipInputStream(counted).use { zip ->
            while (projectText == null || slaveText == null) {
                val entry = zip.nextEntry ?: break
                entries++
                when {
                    entry.isDirectory -> Unit
                    entry.name == ArchiveNames.PROJECT && projectText == null ->
                        projectText = readEntry(zip, limits.maxEntryBytes, entry.name) ?: return tooLarge(entry.name, limits)
                    entry.name == ArchiveNames.SLAVE && slaveText == null ->
                        slaveText = readEntry(zip, limits.maxEntryBytes, entry.name) ?: return tooLarge(entry.name, limits)
                    else -> skipped += entry.name
                }
            }
        }
    } catch (e: ArchiveTooLarge) {
        return ImportRead.Failed("文件超过 ${limits.maxArchiveBytes / (1024 * 1024)} MB，未找到全部配置")
    } catch (e: ZipException) {
        return ImportRead.Failed("不是有效的 ZIP 文件")
    } catch (e: java.io.IOException) {
        return ImportRead.Failed("读取失败：${e.message ?: e.javaClass.simpleName}")
    }
    if (entries == 0) return ImportRead.Failed("不是有效的 ZIP 文件")
    if (projectText == null && slaveText == null) {
        return ImportRead.Failed("ZIP 里没有 ${ArchiveNames.PROJECT} 或 ${ArchiveNames.SLAVE}，没有可导入的配置")
    }
    val project = projectText?.let { text -> parseProject(text).getOrElse { return ImportRead.Failed(it.message.orEmpty()) } }
    val slave = slaveText?.let { text -> parseSlave(text).getOrElse { return ImportRead.Failed(it.message.orEmpty()) } }
    return ImportRead.Preview(project, slave, skipped)
}

/** 解析并检查版本；更新版本写的文件不导入（导进来也只能只读，不如直接说明）。 */
fun parseProject(text: String): Result<ProjectImport> {
    val parsed = try {
        ProjectJson.decode(text)
    } catch (e: Exception) {
        return Result.failure(ImportRejected("工程配置格式错误：${e.message ?: e.javaClass.simpleName}"))
    }
    if (parsed.version > Project.FORMAT_VERSION) {
        return Result.failure(ImportRejected("工程配置来自更新版本（v${parsed.version}），当前版本不能导入"))
    }
    val project = parsed.project
    return Result.success(
        ProjectImport(
            text = text,
            name = project.name,
            panels = project.panels.size,
            widgets = project.panels.sumOf { it.widgets.size },
            points = pointsForExport(project).size,
            warnings = parsed.warnings,
        ),
    )
}

fun parseSlave(text: String): Result<SlaveImport> {
    val parsed = try {
        SlaveSetupJson.decode(text)
    } catch (e: Exception) {
        return Result.failure(ImportRejected("从站配置格式错误：${e.message ?: e.javaClass.simpleName}"))
    }
    if (parsed.version > SlaveSetupJson.FORMAT_VERSION) {
        return Result.failure(ImportRejected("从站配置来自更新版本（v${parsed.version}），当前版本不能导入"))
    }
    val setup = parsed.setup
    return Result.success(
        SlaveImport(
            text = text,
            unitIds = setup.unitIds,
            regions = setup.units.sumOf { it.regions.size },
            rules = setup.rules.size,
            warnings = parsed.warnings,
        ),
    )
}

class ImportRejected(message: String) : Exception(message)

private fun tooLarge(name: String, limits: ImportLimits) =
    ImportRead.Failed("$name 解压后超过 ${limits.maxEntryBytes / (1024 * 1024)} MB，不像本应用导出的配置")

/** 读满 [max] 字节还没结束就返回 null。必须是合法 UTF-8。 */
private fun readEntry(zip: ZipInputStream, max: Int, name: String): String? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = zip.read(buffer)
        if (n < 0) break
        if (out.size() + n > max) return null
        out.write(buffer, 0, n)
    }
    val decoder = Charsets.UTF_8.newDecoder()
    return try {
        decoder.decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString().removePrefix("\uFEFF")
    } catch (e: java.nio.charset.CharacterCodingException) {
        throw java.io.IOException("$name 不是 UTF-8 文本")
    }
}

private class ArchiveTooLarge : java.io.IOException()

/** 读过 [max] 字节就抛 [ArchiveTooLarge]，SAF 给的流可能很大。 */
private class CappedInput(input: InputStream, private val max: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) bump(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) bump(n.toLong())
        return n
    }

    override fun skip(n: Long): Long = super.skip(n).also { if (it > 0) bump(it) }

    private fun bump(n: Long) {
        count += n
        if (count > max) throw ArchiveTooLarge()
    }
}
