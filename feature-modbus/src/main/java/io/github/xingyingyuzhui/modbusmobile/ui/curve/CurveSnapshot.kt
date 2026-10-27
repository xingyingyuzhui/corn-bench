package io.github.xingyingyuzhui.modbusmobile.ui.curve

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SnapStep { NONE, PRIVACY, CHOOSE }

internal fun snapshotFileName(nowMs: Long): String =
    "modbus-curve-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMs)) + ".png"

/**
 * 曲线快照：把全屏页的“图 + 读数”截成一张 PNG。图里有点位名称和数值，所以和日志导出一样
 * **每次保存前先弹隐私提示**（取消 = 什么都不写）→ “保存到文件…”（系统保存界面）或“分享…”（cache/exports + FileProvider）。
 * 不申请存储权限，不联网。系统保存 / 分享界面只能在真机上验证。
 */
@Stable
class CurveSnapshotSaver internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val layer: GraphicsLayer,
    private val launchCreate: (String) -> Unit,
) {
    private var step by mutableStateOf(SnapStep.NONE)
    private var bitmap: Bitmap? = null

    /** 点“快照”：先把当前画面截下来（之后的数据变化不影响这张图），再弹隐私提示。 */
    fun begin() {
        scope.launch {
            val shot = runCatching { layer.toImageBitmap().asAndroidBitmap() }.getOrNull()
            if (shot == null) {
                Toast.makeText(context, R.string.curve_snap_failed_capture, Toast.LENGTH_SHORT).show()
            } else {
                bitmap = shot
                step = SnapStep.PRIVACY
            }
        }
    }

    internal val pending: Boolean get() = step != SnapStep.NONE

    internal fun write(uri: android.net.Uri?) {
        uri ?: return
        val shot = bitmap ?: return
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
        val shot = bitmap ?: return
        step = SnapStep.NONE
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() } // 只留最近一次，别在 cache 里堆积
                    val file = File(dir, snapshotFileName(System.currentTimeMillis()))
                    file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    FileProvider.getUriForFile(context, context.packageName + ".exports", file)
                }
            }
            result.onSuccess { uri ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.log_save_failed, it.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cancel() {
        step = SnapStep.NONE
        bitmap = null
    }

    @Composable
    fun Dialogs() {
        val colors = LocalModbusColors.current
        when (step) {
            SnapStep.NONE -> Unit
            SnapStep.PRIVACY -> AlertDialog(
                onDismissRequest = ::cancel,
                containerColor = colors.surface1,
                title = { Text(stringResource(R.string.log_privacy_title)) },
                text = { Text(stringResource(R.string.curve_snap_privacy_message)) },
                confirmButton = { TextButton(onClick = { step = SnapStep.CHOOSE }) { Text(stringResource(R.string.log_privacy_continue)) } },
                dismissButton = { TextButton(onClick = ::cancel) { Text(stringResource(R.string.action_cancel)) } },
            )
            SnapStep.CHOOSE -> AlertDialog(
                onDismissRequest = ::cancel,
                containerColor = colors.surface1,
                title = { Text(stringResource(R.string.curve_snap_title)) },
                text = { Text(stringResource(R.string.curve_snap_choose)) },
                confirmButton = {
                    TextButton(onClick = {
                        step = SnapStep.NONE
                        launchCreate(snapshotFileName(System.currentTimeMillis()))
                    }) { Text(stringResource(R.string.log_save_to_file)) }
                },
                dismissButton = {
                    androidx.compose.foundation.layout.Column {
                        TextButton(onClick = ::share) { Text(stringResource(R.string.log_save_share)) }
                        TextButton(onClick = ::cancel) { Text(stringResource(R.string.action_cancel)) }
                    }
                },
            )
        }
    }
}

@Composable
fun rememberCurveSnapshotSaver(layer: GraphicsLayer): CurveSnapshotSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var holder: CurveSnapshotSaver? = null
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { holder?.write(it) }
    val saver = remember(context, layer) { CurveSnapshotSaver(context, scope, layer) { create.launch(it) } }
    holder = saver
    return saver
}
