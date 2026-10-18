package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ButtonRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.FieldRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusMotion
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSize
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusType
import io.github.xingyingyuzhui.modbusmobile.ui.theme.RowRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.StrokeControl
import kotlinx.coroutines.delay

/*
 * 反馈组件（设计规范_v1 §6.9、§8.1）。T-20261018-and-08（CL-03）。只用 Compose / Material3 现有组件，无新依赖。
 * - ResultBanner：写入/读取结果，挂在发起操作的控件附近（工作台写入按钮正上方；面板组件卡底用紧凑版）。
 * - ErrorBanner：连接/读取失败：原因 + 怎么办 + “详情”折叠（原始异常）。
 * - UndoBar / UndoBarHost：可撤销的即时修改，Dock 上方 8dp，5s 消失，同时只有一条（新的替换旧的）；也用于替代 Toast 的提示。
 */

/** 结果三态（§6.9）。成功 3s 自动收起；失败、未知不自动收起。 */
enum class ResultKind { SUCCESS, FAILURE, UNKNOWN }

/**
 * 一条结果。[id] 每次新结果都要不同（同样文字的第二次写入也要重新计时），调用方用递增序号或时间戳。
 * 未知结果一律不自动重试（契约 v1 §4），只给“回读”（ResultBanner 的 onReadBack）。
 */
data class ResultNotice(
    val kind: ResultKind,
    val message: String,
    val id: Long,
)

/** 结果条内边距：水平 12（space.xl）/ 垂直 10（space.l），§6.9。 */
private val BannerPaddingVertical: Dp @Composable get() = LocalSpacing.current.gapM

/** 紧凑版结果条（面板卡底）总高 36（§6.9）；= 动作按钮视觉高 32 + 上下 2。 */
private val CompactBannerMinHeight = 36.dp

private fun ModbusColors.tone(kind: ResultKind): Color = when (kind) {
    ResultKind.SUCCESS -> success
    ResultKind.FAILURE -> error
    ResultKind.UNKNOWN -> warning
}

/** 结果条的图标：成功 = 勾、失败 = 叉、未知 = WarnIcon（§1-P2：颜色不单独承载信息，灰度下靠图标 + 文字区分）。 */
@Composable
private fun ResultIcon(kind: ResultKind, color: Color) {
    val size = ModbusSize.iconM
    when (kind) {
        ResultKind.SUCCESS -> CheckGlyph(color, size)
        ResultKind.FAILURE -> CloseIcon(color, size = size)
        ResultKind.UNKNOWN -> WarnIcon(color, size = size)
    }
}

/**
 * 勾号。CL-07（图标卡）会在 Glyphs.kt 补正式的 CheckIcon，届时这里换成它；描边按 §9.2 固定 1.5dp。
 */
@Composable
private fun CheckGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = StrokeControl.toPx()
        drawLine(color, Offset(w * 0.2f, h * 0.52f), Offset(w * 0.42f, h * 0.74f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.74f), Offset(w * 0.8f, h * 0.28f), stroke, StrokeCap.Round)
    }
}

/**
 * 结果条右侧的动作（“回读”“重试”）。外观同 SmallButton（视觉高 32、圆角 10、label 12 SemiBold），
 * 但不撑大布局：触控区由 Compose 默认的最小触控目标扩展补到 48dp，紧凑版才能保持 36 高。
 */
@Composable
private fun BannerAction(text: String, loading: Boolean, loadingText: String, tag: String, onClick: () -> Unit) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(FieldRadius)
    Box(
        Modifier
            .testTag(tag)
            .height(ModbusSize.buttonSmall)
            .clip(shape)
            .border(StrokeControl, colors.line, shape)
            .semantics {
                role = Role.Button
                if (loading) {
                    stateDescription = loadingText
                    disabled()
                }
            }
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = LocalSpacing.current.card),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.s)) {
            if (loading) ButtonProgress(colors.text, ModbusSize.progressSmall)
            Text(
                if (loading) loadingText else text,
                color = colors.text,
                style = ModbusType.label.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}

const val ResultBannerTag = "result-banner"
const val ResultBannerReadBackTag = "result-banner-readback"
const val ResultBannerRetryTag = "result-banner-retry"

/**
 * 写入/读取结果条（§6.9）。语义 wash 底、圆角 12、内边距 12/10、左侧 16dp 图标 + 一句结果（bodyS）+ 右侧动作。
 * - 成功：success，[ModbusMotion.SUCCESS_DISMISS]（3s）后自动收起并回调 [onDismiss]。
 * - 失败：error + “重试”（传了 [onRetry] 才有），不自动收起。
 * - 未知：warning + WarnIcon + “回读”（[onReadBack]），不自动收起；[readBackLoading] 时按钮显示“正在回读…”且不可点。
 * [notice] 为 null 时收起（带 200ms 动画，§8.1 motion.standard）。[compact] = 面板卡底版，总高 36。
 */
@Composable
fun ResultBanner(
    notice: ResultNotice?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onReadBack: (() -> Unit)? = null,
    readBackLoading: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
) {
    val dismiss by rememberUpdatedState(onDismiss)
    var hiddenId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(notice?.id, notice?.kind) {
        if (notice != null && notice.kind == ResultKind.SUCCESS) {
            delay(ModbusMotion.SUCCESS_DISMISS)
            hiddenId = notice.id
            dismiss()
        }
    }
    val visible = notice != null && hiddenId != notice.id
    // 收起动画期间继续画最后一条
    var last by remember { mutableStateOf(notice) }
    if (notice != null) last = notice
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(ModbusMotion.STANDARD)) + expandVertically(tween(ModbusMotion.STANDARD)),
        exit = fadeOut(tween(ModbusMotion.STANDARD)) + shrinkVertically(tween(ModbusMotion.STANDARD)),
    ) {
        val shown = last ?: return@AnimatedVisibility
        ResultBannerContent(shown, compact, onReadBack, readBackLoading, onRetry)
    }
}

@Composable
private fun ResultBannerContent(
    notice: ResultNotice,
    compact: Boolean,
    onReadBack: (() -> Unit)?,
    readBackLoading: Boolean,
    onRetry: (() -> Unit)?,
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val tone = colors.tone(notice.kind)
    val kindLabel = stringResource(
        when (notice.kind) {
            ResultKind.SUCCESS -> R.string.feedback_kind_success
            ResultKind.FAILURE -> R.string.feedback_kind_failure
            ResultKind.UNKNOWN -> R.string.feedback_kind_unknown
        },
    )
    Row(
        Modifier
            .testTag(ResultBannerTag)
            .fillMaxWidth()
            .then(if (compact) Modifier.heightIn(min = CompactBannerMinHeight) else Modifier)
            .clip(RoundedCornerShape(RowRadius))
            .background(colors.wash(tone))
            .semantics(mergeDescendants = false) {
                liveRegion = LiveRegionMode.Polite
                stateDescription = kindLabel
            }
            .padding(horizontal = spacing.card, vertical = if (compact) spacing.xxs else BannerPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.gapS),
    ) {
        Box(Modifier.semantics { contentDescription = kindLabel }) { ResultIcon(notice.kind, tone) }
        Text(
            notice.message,
            color = tone,
            style = ModbusType.bodyS,
            maxLines = if (compact) 1 else 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when {
            notice.kind == ResultKind.UNKNOWN && onReadBack != null -> BannerAction(
                stringResource(R.string.feedback_read_back),
                readBackLoading,
                stringResource(R.string.feedback_read_back_loading),
                ResultBannerReadBackTag,
                onReadBack,
            )
            notice.kind == ResultKind.FAILURE && onRetry != null -> BannerAction(
                stringResource(R.string.feedback_retry),
                false,
                stringResource(R.string.btn_loading_generic),
                ResultBannerRetryTag,
                onRetry,
            )
        }
    }
}

const val ErrorBannerTag = "error-banner"
const val ErrorBannerDetailsTag = "error-banner-details"

/**
 * 连接/读取失败（§6.9 ErrorBanner、§7.3、§10.4）：一句原因 [reason] + 一句怎么办 [hint] 或一个按钮（[actionLabel] + [onAction]），
 * 原始异常 [details] 只放“详情”折叠里（monoSmall），不直接展示。
 */
@Composable
fun ErrorBanner(
    reason: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    details: String? = null,
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val showDetails = stringResource(R.string.feedback_details_show)
    val hideDetails = stringResource(R.string.feedback_details_hide)
    val expandedState = stringResource(if (expanded) R.string.feedback_state_expanded else R.string.feedback_state_collapsed)
    Column(
        modifier
            .testTag(ErrorBannerTag)
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowRadius))
            .background(colors.wash(colors.error))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = spacing.card, vertical = BannerPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.gapS)) {
            CloseIcon(colors.error, size = ModbusSize.iconM)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(reason, color = colors.error, style = ModbusType.bodyS)
                if (hint != null) Text(hint, color = colors.text, style = ModbusType.bodyS)
            }
            if (actionLabel != null && onAction != null) {
                BannerAction(actionLabel, false, stringResource(R.string.btn_loading_generic), "error-banner-action", onAction)
            }
        }
        if (details != null) {
            Text(
                if (expanded) hideDetails else showDetails,
                color = colors.muted,
                style = ModbusType.label.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .testTag(ErrorBannerDetailsTag)
                    .heightIn(min = ModbusSize.touch)
                    .semantics { stateDescription = expandedState }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = spacing.s),
            )
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(ModbusMotion.STANDARD)) + expandVertically(tween(ModbusMotion.STANDARD)),
                exit = fadeOut(tween(ModbusMotion.STANDARD)) + shrinkVertically(tween(ModbusMotion.STANDARD)),
            ) {
                Text(details, color = colors.muted, style = ModbusType.monoSmall)
            }
        }
    }
}

/** 一条撤销条 / 提示条。[actionLabel] 为 null 时只是提示（替代 Toast，§6.9 Snackbar 位置）。 */
data class UndoMessage(
    val message: String,
    val id: Long,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * 撤销条状态：同时只有一条，[show] 新的替换旧的（旧的不会再触发撤销）。放在屏幕级别 remember，交给 [UndoBarHost] 画。
 */
@Stable
class UndoBarState {
    var current: UndoMessage? by mutableStateOf(null)
        private set
    private var seq = 0L

    /** 显示一条；返回它的 id。 */
    fun show(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null): Long {
        seq += 1
        current = UndoMessage(message, seq, actionLabel, onAction)
        return seq
    }

    /** 收起指定 id（默认当前这条）；已被替换的旧 id 不影响新的那条。 */
    fun dismiss(id: Long? = current?.id) {
        if (id != null && current?.id == id) current = null
    }
}

@Composable
fun rememberUndoBarState(): UndoBarState = remember { UndoBarState() }

const val UndoBarTag = "undo-bar"
const val UndoBarActionTag = "undo-bar-action"

/**
 * 撤销条宿主（§6.9）：在所在 Box 底部居中，离 Dock 上沿 8dp（LocalDockInsets.bottom + space.m；没有 Dock 时离底 8dp），
 * 左右 page 边距。每条 [ModbusMotion.UNDO]（5s）后自动消失；点“撤销”先执行动作再收起。
 * 用法：屏幕根部 `Box { 内容; UndoBarHost(state) }`。
 */
@Composable
fun BoxScope.UndoBarHost(state: UndoBarState, modifier: Modifier = Modifier) {
    val dock = LocalDockInsets.current
    val spacing = LocalSpacing.current
    val current = state.current
    LaunchedEffect(current?.id) {
        val id = current?.id ?: return@LaunchedEffect
        delay(ModbusMotion.UNDO)
        state.dismiss(id)
    }
    var last by remember { mutableStateOf(current) }
    if (current != null) last = current
    AnimatedVisibility(
        visible = current != null,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(start = spacing.page, end = spacing.page, bottom = dock.bottom + spacing.gapS),
        enter = fadeIn(tween(ModbusMotion.STANDARD)),
        exit = fadeOut(tween(ModbusMotion.STANDARD)),
    ) {
        val shown = last ?: return@AnimatedVisibility
        UndoBar(shown) {
            shown.onAction?.invoke()
            state.dismiss(shown.id)
        }
    }
}

/** 撤销条本体：surface3 底、圆角 14、body 14 + TextLink“撤销”（accent.text）。 */
@Composable
fun UndoBar(message: UndoMessage, modifier: Modifier = Modifier, onAction: () -> Unit) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    Row(
        modifier
            .testTag(UndoBarTag)
            .fillMaxWidth()
            .heightIn(min = ModbusSize.touch)
            .clip(RoundedCornerShape(ButtonRadius))
            .background(colors.surface3)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = spacing.card, end = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message.message, color = colors.text, style = ModbusType.body, modifier = Modifier.weight(1f))
        if (message.actionLabel != null) {
            TextLink(message.actionLabel, onAction, Modifier.testTag(UndoBarActionTag), color = colors.accentText)
        }
    }
}

// ---- 预览：成功 / 失败 / 未知（含紧凑）+ ErrorBanner + UndoBar，深、浅、1.3x ----

@Preview(name = "反馈 深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedbackDark() = FeedbackPreview(true)

@Preview(name = "反馈 浅色", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun FeedbackLight() = FeedbackPreview(false)

@Preview(name = "反馈 浅色 1.3x", fontScale = 1.3f)
@Composable
private fun FeedbackLarge() = FeedbackPreview(false)

@Composable
private fun FeedbackPreview(dark: Boolean) {
    ModbusTheme(UiSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        val colors = LocalModbusColors.current
        val spacing = LocalSpacing.current
        val unknown = stringResource(R.string.feedback_unknown_write)
        Box(Modifier.background(colors.bg)) {
            Column(Modifier.padding(spacing.page), verticalArrangement = Arrangement.spacedBy(spacing.gapS)) {
                ResultBanner(ResultNotice(ResultKind.SUCCESS, stringResource(R.string.feedback_preview_success), 1))
                ResultBanner(ResultNotice(ResultKind.FAILURE, stringResource(R.string.feedback_preview_failure), 2), onRetry = {})
                ResultBanner(ResultNotice(ResultKind.UNKNOWN, unknown, 3), onReadBack = {})
                ResultBanner(ResultNotice(ResultKind.UNKNOWN, unknown, 4), onReadBack = {}, readBackLoading = true)
                ResultBanner(ResultNotice(ResultKind.UNKNOWN, unknown, 5), compact = true, onReadBack = {})
                ErrorBanner(
                    stringResource(R.string.feedback_preview_error_reason),
                    hint = stringResource(R.string.feedback_preview_error_hint),
                    actionLabel = stringResource(R.string.feedback_retry),
                    onAction = {},
                    details = "TimeoutException: no response in 2000 ms",
                )
                UndoBar(UndoMessage(stringResource(R.string.feedback_preview_undo), 1, stringResource(R.string.feedback_undo))) {}
            }
        }
    }
}
