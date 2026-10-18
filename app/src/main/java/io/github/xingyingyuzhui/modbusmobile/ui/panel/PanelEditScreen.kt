package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectEdits
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.ui.components.CloseIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.SubPageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSize
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.SelectChip
import io.github.xingyingyuzhui.modbusmobile.ui.components.SmallButton
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.pageStart
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ButtonRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.FieldRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import kotlin.math.roundToInt

/** 编辑模式用到的操作（界面不直接碰 ViewModel，方便测试）。 */
data class EditActions(
    val onRename: (String) -> Unit = {},
    val onColumns: (Int) -> Unit = {},
    val onMove: (widgetId: String, targetId: String) -> Unit = { _, _ -> },
    val onSpan: (widgetId: String, columns: Int, rows: Int) -> Unit = { _, _, _ -> },
    val onDelete: (String) -> Unit = {},
    val onSelectPanel: (String) -> Unit = {},
    val onAddPanel: () -> Unit = {},
    val onDeletePanel: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onCancel: () -> Unit = {},
    /** 打开添加向导 / 点卡片配置这个组件。 */
    val onAddWidget: () -> Unit = {},
    val onOpenWidget: (String) -> Unit = {},
)

/**
 * 面板编辑模式（稿面 b2-2）：✕ + “编辑面板” + 琥珀 xs“完成”；面板名称 / 列数两格；组件卡虚线框 +
 * 左上 ⠿ + 右上红 × + 右下琥珀角标；底注。所有修改在草稿里，“完成”才写盘，✕ 放弃（有修改先确认）。
 * 底部“＋ 添加组件”（虚线按钮）打开添加向导（PanelWizardScreen）；点一张卡 = 用同一个向导改这个组件。
 */
@Composable
fun PanelEditor(
    edit: EditState,
    snapshots: Map<String, PointSnapshot>,
    actions: EditActions,
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    val panel: Panel = edit.draft.panels.firstOrNull { it.id == edit.panelId } ?: edit.draft.panels.first()
    val columns = if (landscape) 4 else panel.columns
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val requestCancel = { if (edit.dirty) confirmDiscard = true else actions.onCancel() }
    BackHandler(onBack = requestCancel)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // AL-03：关闭钮和其它二级页的返回钮同一位置（SubPageHeader 统一处理），不再自己减 8。
        SubPageHeader(
            title = stringResource(R.string.panel_edit_title),
            onBack = requestCancel,
            modifier = Modifier.padding(start = pageStart(), end = spacing.page),
            leadingDescription = stringResource(R.string.panel_edit_close),
            leadingTag = "edit-close",
            leadingIcon = { CloseIcon(it, size = ModbusSize.iconM) },
        ) {
            SmallButton(
                stringResource(R.string.panel_edit_done), actions.onDone, Modifier.testTag("edit-done"),
                primary = true, enabled = !edit.saving,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = pageStart(), end = spacing.page, top = 8.dp, bottom = dockClearance()),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput(
                    label = stringResource(R.string.panel_edit_name),
                    value = panel.name,
                    modifier = Modifier.weight(1f).testTag("edit-name"),
                    minHeight = 44.dp,
                    keyboardType = KeyboardType.Text,
                    error = if (!ProjectEdits.isValidName(panel.name)) stringResource(R.string.panel_edit_name_blank) else null,
                    onValue = actions.onRename,
                )
                ColumnsTile(panel.columns, landscape, actions.onColumns, Modifier.width(110.dp).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                edit.draft.panels.forEach { p ->
                    SelectChip(p.name.ifBlank { "…" }, p.id == panel.id) { actions.onSelectPanel(p.id) }
                }
                SelectChip("＋", false, description = stringResource(R.string.panel_edit_new_panel)) { actions.onAddPanel() }
            }
            val widgets = panel.widgets.filter { it.isUsable }
            if (widgets.isEmpty()) {
                Text(
                    stringResource(R.string.panel_edit_empty), color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(vertical = 4.dp).testTag("edit-empty"),
                )
            } else {
                EditGrid(widgets, columns, panel.columns, snapshots, actions, spacing.s)
            }
            AddWidgetButton(actions.onAddWidget)
            if (edit.draft.panels.size > 1) {
                Box(
                    Modifier
                        .heightIn(min = MinTouch)
                        .clip(RoundedCornerShape(FieldRadius))
                        .clickable { confirmDelete = true }
                        .padding(horizontal = 4.dp)
                        .testTag("edit-delete-panel"),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(stringResource(R.string.panel_edit_delete_panel), color = colors.error, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val error = when (edit.error) {
                EditError.BLANK_NAME -> R.string.panel_edit_name_blank
                EditError.SAVE_FAILED -> R.string.panel_edit_error_save
                EditError.READ_ONLY -> R.string.panel_edit_error_readonly
                null -> null
            }
            if (error != null) {
                Text(stringResource(error), color = colors.error, fontSize = 12.sp, modifier = Modifier.testTag("edit-error"))
            }
            Text(
                stringResource(R.string.panel_edit_hint), color = colors.muted, fontSize = 11.5.sp, lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().testTag("edit-hint"),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.panel_edit_discard_title),
            body = stringResource(R.string.panel_edit_discard_body),
            confirm = stringResource(R.string.panel_edit_discard),
            dismiss = stringResource(R.string.panel_edit_keep),
            onConfirm = { confirmDiscard = false; actions.onCancel() },
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.panel_edit_delete_panel_title, panel.name),
            body = stringResource(R.string.panel_edit_delete_panel_body, panel.widgets.count { it.isUsable }),
            confirm = stringResource(R.string.panel_edit_delete_confirm),
            dismiss = stringResource(R.string.action_cancel),
            onConfirm = { confirmDelete = false; actions.onDeletePanel() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, dismiss: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalModbusColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface1,
        titleContentColor = colors.text,
        textContentColor = colors.muted,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = { Text(body, fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("edit-dialog-confirm")) { Text(confirm, color = colors.error) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("edit-dialog-dismiss")) { Text(dismiss, color = colors.accentText) } },
    )
}

/** 列数格：整格可点，2 → 3 → 4 → 2 循环（热区 = 整格，高 ≥48）。横屏固定 4 列，这个值只管竖屏。 */
@Composable
private fun ColumnsTile(columns: Int, landscape: Boolean, onColumns: (Int) -> Unit, modifier: Modifier) {
    val colors = LocalModbusColors.current
    val description = stringResource(R.string.panel_edit_columns_cd, columns)
    Column(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(FieldRadius))
            .background(colors.surface2)
            .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button }
            .clickable {
                onColumns(if (columns >= ProjectEdits.MAX_COLUMNS) ProjectEdits.MIN_COLUMNS else columns + 1)
            }
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("edit-columns"),
    ) {
        Text(stringResource(R.string.panel_edit_columns), color = colors.muted, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
        Text(
            columns.toString(), color = colors.text, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = io.github.xingyingyuzhui.modbusmobile.ui.theme.JetBrainsMono,
            modifier = Modifier.testTag("edit-columns-value"),
        )
    }
}

// ---- 可拖动的网格 ----

@Composable
private fun EditGrid(
    widgets: List<Widget>,
    columns: Int,
    panelColumns: Int,
    snapshots: Map<String, PointSnapshot>,
    actions: EditActions,
    gap: Dp,
) {
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hoverId by remember { mutableStateOf<String?>(null) }
    var resizeId by remember { mutableStateOf<String?>(null) }
    var resizePreview by remember { mutableStateOf(1 to 1) }
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val spans = widgets.map { it.spanColumns }

    PanelGrid(
        columns = columns,
        spans = spans,
        spanRows = widgets.map { it.spanRows },
        modifier = Modifier.fillMaxWidth().testTag("edit-grid"),
        gap = gap,
        // 被拖的那一行画在最上面，不被后面的行盖住
        rowModifier = { indices -> if (indices.any { widgets[it].id == dragId }) Modifier.zIndex(1f) else Modifier },
    ) { index ->
        val widget = widgets[index]
        val dragging = dragId == widget.id
        val title = widget.displayTitle
        val moveEarlier = stringResource(R.string.panel_edit_move_earlier)
        val moveLater = stringResource(R.string.panel_edit_move_later)
        val resizeDescription = stringResource(R.string.panel_edit_resize, title)
        val deleteDescription = stringResource(R.string.panel_edit_delete_widget, title)
        val openLabel = stringResource(R.string.panel_edit_open_widget, title)
        Box(
            Modifier
                .fillMaxSize()
                .heightIn(min = 96.dp)
                .onGloballyPositioned { bounds[widget.id] = it.boundsInRoot() }
                .graphicsLayer {
                    if (dragging) {
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                        alpha = 0.92f
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
                }
                .semantics {
                    customActions = listOfNotNull(
                        widgets.getOrNull(index - 1)?.let { prev ->
                            CustomAccessibilityAction(moveEarlier) { actions.onMove(widget.id, prev.id); true }
                        },
                        widgets.getOrNull(index + 1)?.let { next ->
                            CustomAccessibilityAction(moveLater) { actions.onMove(widget.id, next.id); true }
                        },
                    )
                }
                .clickable(onClickLabel = openLabel) { actions.onOpenWidget(widget.id) }
                .pointerInput(widget.id, widgets.map { it.id }) {
                    var start = Offset.Zero
                    detectDragGesturesAfterLongPress(
                        onDragStart = { local ->
                            start = local
                            dragId = widget.id
                            dragOffset = Offset.Zero
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount
                            val origin = bounds[widget.id]?.topLeft ?: Offset.Zero
                            // onGloballyPositioned 在 graphicsLayer 之前，记录的是没有位移的原位置
                            val finger = origin + start + dragOffset
                            hoverId = bounds.entries.firstOrNull { (id, rect) -> id != widget.id && rect.contains(finger) }?.key
                        },
                        onDragEnd = {
                            hoverId?.let { actions.onMove(widget.id, it) }
                            dragId = null; hoverId = null; dragOffset = Offset.Zero
                        },
                        onDragCancel = { dragId = null; hoverId = null; dragOffset = Offset.Zero },
                    )
                }
                .testTag("edit-card-${widget.id}"),
        ) {
            if (widget.curve != null) {
                io.github.xingyingyuzhui.modbusmobile.ui.curve.CurveEditCard(widget, Modifier.fillMaxSize())
            } else {
                WidgetCard(widget, snapshots[widget.point?.id], Modifier.fillMaxSize(), editing = true)
            }
            DashedOutline(if (hoverId == widget.id) LocalModbusColors.current.accent else LocalModbusColors.current.dim)
            // 右上红 ×：视觉 20dp 圆，画在卡片角上（稿面 right/top -6）；热区 48×48 在卡片内。
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .semantics { contentDescription = deleteDescription; role = Role.Button }
                    .clickable { actions.onDelete(widget.id) }
                    .drawBehind {
                        val r = 10.dp.toPx()
                        drawCircle(Color(0xFFF85149), r, Offset(size.width - 4.dp.toPx(), 4.dp.toPx()))
                        val c = Offset(size.width - 4.dp.toPx(), 4.dp.toPx())
                        val arm = 3.5.dp.toPx()
                        val stroke = 1.6.dp.toPx()
                        drawLine(Color.White, c + Offset(-arm, -arm), c + Offset(arm, arm), stroke)
                        drawLine(Color.White, c + Offset(arm, -arm), c + Offset(-arm, arm), stroke)
                    }
                    .testTag("edit-delete-${widget.id}"),
            )
            // 右下琥珀角标：拖 = 改尺寸，点一下 = 切换尺寸；热区 48×48 在卡片内。
            val accent = LocalModbusColors.current.accent
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(48.dp)
                    .semantics { contentDescription = resizeDescription; role = Role.Button }
                    .pointerInput(widget.id, panelColumns) {
                        detectTapGestures {
                            val (c, r) = ProjectEdits.nextSize(widget.spanColumns, widget.spanRows, panelColumns, widget.minColumns)
                            actions.onSpan(widget.id, c, r)
                        }
                    }
                    .pointerInput(widget.id, panelColumns, widgets.map { it.id }) {
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero; resizeId = widget.id; resizePreview = widget.spanColumns to widget.spanRows },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                                val rect = bounds[widget.id] ?: return@detectDragGestures
                                resizePreview = resizeFor(rect, total, widget, columns, panelColumns, gapPx, bounds)
                            },
                            onDragEnd = {
                                actions.onSpan(widget.id, resizePreview.first, resizePreview.second)
                                resizeId = null
                            },
                            onDragCancel = { resizeId = null },
                        )
                    }
                    .drawBehind {
                        val s = 12.dp.toPx()
                        val x = size.width - 6.dp.toPx()
                        val y = size.height - 6.dp.toPx()
                        val stroke = 2.dp.toPx()
                        drawLine(accent, Offset(x, y - s), Offset(x, y), stroke)
                        drawLine(accent, Offset(x - s, y), Offset(x, y), stroke)
                    }
                    .testTag("edit-handle-${widget.id}"),
            )
            if (resizeId == widget.id) {
                Text(
                    stringResource(R.string.panel_edit_size, resizePreview.first, resizePreview.second),
                    color = LocalModbusColors.current.text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center).testTag("edit-size-preview"),
                )
            }
        }
    }
}

private fun resizeFor(
    rect: Rect,
    total: Offset,
    widget: Widget,
    columns: Int,
    panelColumns: Int,
    gapPx: Float,
    bounds: Map<String, Rect>,
): Pair<Int, Int> {
    // 一列的宽 / 一行的高：由当前卡片尺寸和它占的列 / 行数反推。
    val cell = (rect.width - gapPx * (widget.spanColumns - 1)) / widget.spanColumns
    val rowHeight = (rect.height - gapPx * (widget.spanRows - 1)) / widget.spanRows
    return ProjectEdits.resizedSpan(rect.width, rect.height, total.x, total.y, cell, rowHeight, gapPx, minOf(columns, panelColumns), widget.minColumns)
}

/** 稿面 `.edit .w{outline:1.5px dashed var(--dim);outline-offset:-4px}`：虚线框内缩 4，圆角随内缩 16−4=12。 */
@Composable
private fun DashedOutline(color: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val inset = 4.dp.toPx()
                val dash = 5.dp.toPx()
                drawRoundRect(
                    color,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius((CardRadius.toPx() - inset)),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                )
            }
            .testTag("edit-outline"),
    )
}

/** 稿面 `.btn.out.sm` 虚线版：高 44（热区补到 48），“＋ 添加组件”。 */
@Composable
private fun AddWidgetButton(onClick: () -> Unit) {
    val colors = LocalModbusColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .height(44.dp)
            .clip(RoundedCornerShape(ButtonRadius))
            .background(colors.surface1)
            .drawBehind {
                drawRoundRect(
                    colors.line, cornerRadius = CornerRadius(ButtonRadius.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .testTag("edit-add-widget"),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋ " + stringResource(R.string.panel_add_widget), color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
