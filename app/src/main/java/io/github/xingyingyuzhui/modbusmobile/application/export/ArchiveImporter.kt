package io.github.xingyingyuzhui.modbusmobile.application.export

import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectSave
import io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetupStore

enum class ImportPart { PROJECT, SLAVE }

sealed interface ImportApply {
    data class Done(val project: Boolean, val slave: Boolean) : ImportApply

    /** 有会话在跑：什么都没改。 */
    data object Blocked : ImportApply

    /** [projectDone]：工程已经替换成功、之后的从站配置失败了。 */
    data class Failed(val reason: String, val projectDone: Boolean) : ImportApply
}

/**
 * 把预览里选中的配置写回。只在没有会话时导入：运行中的从站会把内存配置存回 slave.json，
 * 正在轮询的面板也还在用旧点位；先断开再导入，避免导入的内容被马上覆盖。
 * 导入只替换文件，不连接、不写设备、不启动从站。
 */
class ArchiveImporter(
    private val projects: ProjectRepository?,
    private val slaves: SlaveSetupStore?,
    private val sessionState: () -> SessionState,
) {
    fun canImport(): Boolean = sessionState().let { it is SessionState.Idle || it is SessionState.Error }

    fun apply(preview: ImportRead.Preview, parts: Set<ImportPart>): ImportApply {
        if (!canImport()) return ImportApply.Blocked
        var projectDone = false
        val project = preview.project
        if (ImportPart.PROJECT in parts && project != null) {
            val repo = projects ?: return ImportApply.Failed("工程存储不可用", false)
            when (val saved = repo.importJson(project.text)) {
                ProjectSave.Saved -> projectDone = true
                ProjectSave.ReadOnly -> return ImportApply.Failed("工程配置来自更新版本，未导入", false)
                is ProjectSave.Failed -> return ImportApply.Failed("工程配置写入失败：${saved.message}", false)
            }
        }
        var slaveDone = false
        val slave = preview.slave
        if (ImportPart.SLAVE in parts && slave != null) {
            val store = slaves ?: return ImportApply.Failed("从站配置存储不可用", projectDone)
            if (!store.importJson(slave.text)) return ImportApply.Failed("从站配置写入失败", projectDone)
            slaveDone = true
        }
        return ImportApply.Done(projectDone, slaveDone)
    }
}
