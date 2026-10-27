package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SaveStep { NONE, PRIVACY, CHOOSE }

/**
 * 日志保存：先弹隐私提示（每次保存前都弹，不能关掉）→ 选格式 → “保存到文件…”（系统保存界面 CreateDocument）
 * 或“分享…”（写到 cache/exports，经 FileProvider 分享）。不申请存储权限，不联网。
 * 真实的系统保存 / 分享界面只能在真机上验证（Robolectric 里只验证到隐私提示和选择框）。
 */
@Stable
class LogSaver internal constructor(
    private val model: TrafficViewModel,
    private val context: Context,
    private val scope: CoroutineScope,
    private val launchCsv: (String) -> Unit,
    private val launchTxt: (String) -> Unit,
) {
    private var step by mutableStateOf(SaveStep.NONE)
    private var format by mutableStateOf(ExportFormat.CSV)
    private var snapshot: List<LogItem> = emptyList()

    fun begin() {
        snapshot = model.exportItems()
        if (snapshot.none { it is LogEntry }) {
            Toast.makeText(context, R.string.log_nothing_to_save, Toast.LENGTH_SHORT).show()
            return
        }
        step = SaveStep.PRIVACY
    }

    internal fun write(uri: android.net.Uri?) {
        uri ?: return
        val items = snapshot
        val fmt = format
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { writeLogExport(it, items, fmt) }
                        ?: error("无法打开目标文件")
                }
            }
            Toast.makeText(
                context,
                result.exceptionOrNull()?.let { context.getString(R.string.log_save_failed, it.message ?: "") }
                    ?: context.getString(R.string.log_saved),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun share() {
        val items = snapshot
        val fmt = format
        step = SaveStep.NONE
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() } // 只留最近一次，别在 cache 里堆积
                    val file = File(dir, exportFileName(fmt, System.currentTimeMillis()))
                    file.outputStream().use { writeLogExport(it, items, fmt) }
                    FileProvider.getUriForFile(context, context.packageName + ".exports", file)
                }
            }
            result.onSuccess { uri ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = fmt.mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.log_save_failed, it.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Composable
    fun Dialogs() {
        val colors = LocalModbusColors.current
        when (step) {
            SaveStep.NONE -> Unit
            SaveStep.PRIVACY -> AlertDialog(
                onDismissRequest = { step = SaveStep.NONE },
                containerColor = colors.surface1,
                title = { Text(stringResource(R.string.log_privacy_title)) },
                text = { Text(stringResource(R.string.log_privacy_message)) },
                confirmButton = {
                    TextButton(onClick = { step = SaveStep.CHOOSE }) { Text(stringResource(R.string.log_privacy_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { step = SaveStep.NONE }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
            SaveStep.CHOOSE -> AlertDialog(
                onDismissRequest = { step = SaveStep.NONE },
                containerColor = colors.surface1,
                title = { Text(stringResource(R.string.log_save_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SegmentedTextControl(ExportFormat.entries, format, { format = it }, Modifier) {
                            stringResource(if (it == ExportFormat.CSV) R.string.log_save_format_csv else R.string.log_save_format_txt)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        step = SaveStep.NONE
                        val name = exportFileName(format, System.currentTimeMillis())
                        if (format == ExportFormat.CSV) launchCsv(name) else launchTxt(name)
                    }) { Text(stringResource(R.string.log_save_to_file)) }
                },
                dismissButton = {
                    Column {
                        TextButton(onClick = ::share) { Text(stringResource(R.string.log_save_share)) }
                        TextButton(onClick = { step = SaveStep.NONE }) { Text(stringResource(R.string.action_cancel)) }
                    }
                },
            )
        }
    }
}

@Composable
fun rememberLogSaver(model: TrafficViewModel): LogSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var holder: LogSaver? = null
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)) { holder?.write(it) }
    val txt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.TXT.mime)) { holder?.write(it) }
    val saver = remember(model, context) {
        LogSaver(model, context, scope, { csv.launch(it) }, { txt.launch(it) })
    }
    holder = saver
    return saver
}
