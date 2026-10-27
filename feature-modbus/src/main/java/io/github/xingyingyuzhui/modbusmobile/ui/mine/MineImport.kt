package io.github.xingyingyuzhui.modbusmobile.ui.mine

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.export.ArchiveImporter
import io.github.xingyingyuzhui.modbusmobile.application.export.ImportApply
import io.github.xingyingyuzhui.modbusmobile.application.export.ImportPart
import io.github.xingyingyuzhui.modbusmobile.application.export.ImportRead
import io.github.xingyingyuzhui.modbusmobile.application.export.readArchiveImport
import io.github.xingyingyuzhui.modbusmobile.ui.LocalProjectRepository
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSlaveSetupStore
import io.github.xingyingyuzhui.modbusmobile.ui.components.ErrorBanner
import io.github.xingyingyuzhui.modbusmobile.ui.components.InPageSheet
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 「我的 → 导入配置」：选 ZIP → 读出预览 → 用户勾选并确认 → 替换文件。 */
internal class DeviceImport(
    private val context: Context,
    val importer: ArchiveImporter,
    private val launchPick: () -> Unit,
) {
    var read by mutableStateOf<ImportRead?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    fun pick() {
        if (!importer.canImport()) {
            toast(context.getString(R.string.import_blocked))
            return
        }
        launchPick()
    }

    suspend fun load(uri: Uri?) {
        if (uri == null) return
        busy = true
        read = try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { readArchiveImport(it) }
                    ?: ImportRead.Failed(context.getString(R.string.import_open_failed))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ImportRead.Failed(error.message ?: error.javaClass.simpleName)
        } finally {
            busy = false
        }
    }

    suspend fun apply(preview: ImportRead.Preview, parts: Set<ImportPart>) {
        busy = true
        val result = try {
            withContext(Dispatchers.IO) { importer.apply(preview, parts) }
        } finally {
            busy = false
        }
        when (result) {
            is ImportApply.Done -> {
                read = null
                toast(context.getString(R.string.import_done))
            }
            ImportApply.Blocked -> toast(context.getString(R.string.import_blocked))
            is ImportApply.Failed -> {
                read = null
                val reason = if (result.projectDone) context.getString(R.string.import_partial, result.reason) else result.reason
                toast(context.getString(R.string.import_failed, reason))
            }
        }
    }

    fun dismiss() {
        read = null
    }

    private fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}

@Composable
internal fun rememberDeviceImport(): DeviceImport {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val projects = LocalProjectRepository.current
    val slaves = LocalSlaveSetupStore.current
    val session = LocalSession.current
    val importer = remember(projects, slaves, session) { ArchiveImporter(projects, slaves) { session.state.value } }
    var holder: DeviceImport? = null
    // 有的文件管理器把 .zip 标成 octet-stream，两种都放行；内容由 readArchiveImport 校验。
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        holder?.let { device -> scope.launch { device.load(uri) } }
    }
    val device = remember(importer, context) {
        DeviceImport(context, importer) { launcher.launch(arrayOf("application/zip", "application/octet-stream")) }
    }
    holder = device
    return device
}

@Composable
internal fun ImportSheet(
    read: ImportRead,
    busy: Boolean,
    onImport: (ImportRead.Preview, Set<ImportPart>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalModbusColors.current
    InPageSheet(stringResource(R.string.import_title), onDismiss, tag = "import-sheet") {
        when (read) {
            is ImportRead.Failed -> {
                ErrorBanner(read.reason, hint = stringResource(R.string.import_nothing_changed))
                SecondaryButton(stringResource(R.string.import_close), onDismiss, Modifier.fillMaxWidth(), compact = true)
            }
            is ImportRead.Preview -> {
                var project by remember(read) { mutableStateOf(read.project != null) }
                var slave by remember(read) { mutableStateOf(read.slave != null) }
                val parts = buildSet {
                    if (project && read.project != null) add(ImportPart.PROJECT)
                    if (slave && read.slave != null) add(ImportPart.SLAVE)
                }
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    read.project?.let { p ->
                        ImportRow(
                            "import-part-PROJECT",
                            stringResource(R.string.import_project),
                            stringResource(R.string.import_project_sub, p.name, p.panels, p.widgets, p.points),
                            p.warnings,
                            project,
                        ) { project = it }
                    }
                    read.slave?.let { s ->
                        ImportRow(
                            "import-part-SLAVE",
                            stringResource(R.string.import_slave),
                            stringResource(R.string.import_slave_sub, s.unitIds.joinToString(", "), s.regions, s.rules),
                            s.warnings,
                            slave,
                        ) { slave = it }
                    }
                    if (read.skipped.isNotEmpty()) {
                        Text(
                            stringResource(R.string.import_skipped, read.skipped.joinToString(", ")),
                            color = colors.dim,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.testTag("import-skipped"),
                        )
                    }
                }
                Text(
                    stringResource(R.string.import_replace_note),
                    color = colors.dim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(stringResource(R.string.import_cancel), onDismiss, Modifier.weight(1f), compact = true)
                    PrimaryButton(
                        stringResource(R.string.import_confirm),
                        { if (parts.isNotEmpty()) onImport(read, parts) },
                        Modifier.weight(1f).testTag("import-confirm"),
                        enabled = parts.isNotEmpty() && !busy,
                        compact = true,
                        loading = busy,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportRow(
    tag: String,
    title: String,
    subtitle: String,
    warnings: List<String>,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalModbusColors.current
    Column {
        CheckRow(tag, title, subtitle, on) { onChange(!on) }
        warnings.forEach { warning ->
            Text("⚠ $warning", color = colors.warning, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("$tag-warning"))
        }
    }
}
