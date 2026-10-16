package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.HeaderIconButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.HexFrameText
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.components.PauseIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.PlayIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.SelectChip
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticBadge
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticKind
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.components.TextLink
import io.github.xingyingyuzhui.modbusmobile.ui.components.label
import io.github.xingyingyuzhui.modbusmobile.ui.components.tone
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalUiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

@Composable
fun TrafficScreen() {
    val session = LocalSession.current
    val model = viewModel { TrafficViewModel(session) }
    val entries by model.entries.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val paused by model.paused.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val crcDim = LocalUiSettings.current.logCrcDim
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    LogContent(
        entries = entries,
        filter = filter,
        paused = paused,
        selected = selected,
        crcDim = crcDim,
        landscape = landscape,
        onFilter = model::setFilter,
        onTogglePause = model::togglePaused,
        onSelect = model::select,
    )
}

/** 无状态的页面内容（截图测试直接喂数据）。 */
@Composable
fun LogContent(
    entries: List<LogEntry>,
    filter: LogFilter,
    paused: Boolean,
    selected: Long?,
    crcDim: Boolean,
    landscape: Boolean,
    onFilter: (LogFilter) -> Unit,
    onTogglePause: () -> Unit,
    onSelect: (Long?) -> Unit,
) {
    val spacing = LocalSpacing.current
    val visible = remember(entries, filter) { entries.filter { filter.accepts(it.event.kind, it.info) } }
    val effective = visible.firstOrNull { it.seq == selected } ?: visible.lastOrNull()
    val emptyText = stringResource(if (entries.isEmpty()) R.string.traffic_empty else R.string.log_empty_filtered)
    if (landscape) {
        Row(
            Modifier.screenFrame().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.gapM),
        ) {
            Column(Modifier.weight(1.25f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.title_log),
                        color = LocalModbusColors.current.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    FilterChips(filter, onFilter, xs = true, modifier = Modifier.weight(1f))
                    PauseButton(paused, onTogglePause)
                }
                LogCard(
                    visible, effective?.seq, paused, crcDim, emptyText,
                    onSelect = { onSelect(it) },
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                FrameDetailCard(effective, Modifier.fillMaxWidth())
            }
        }
    } else {
        Column(Modifier.screenFrame()) {
            PageHeader(stringResource(R.string.title_log)) { PauseButton(paused, onTogglePause) }
            SessionStrip(
                Modifier.padding(bottom = spacing.gapS),
                trailing = { LogStripInfo(paused, entries.size) },
            )
            FilterChips(filter, onFilter, xs = false, modifier = Modifier.padding(bottom = 4.dp))
            LogCard(
                visible, null, paused, crcDim, emptyText,
                onSelect = null,
                modifier = Modifier.weight(1f, fill = false).padding(bottom = dockClearance().takeIf { it > 0.dp } ?: spacing.gapS),
            )
        }
    }
}

@Composable
private fun LogStripInfo(paused: Boolean, count: Int) {
    val colors = LocalModbusColors.current
    Column(horizontalAlignment = Alignment.End) {
        Text(stringResource(R.string.log_limit), style = Mono.copy(fontSize = 11.sp, lineHeight = 15.sp), color = colors.muted)
        Text(
            if (paused) stringResource(R.string.log_paused) else stringResource(R.string.log_count, count),
            style = Mono.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = colors.muted,
        )
    }
}

@Composable
private fun PauseButton(paused: Boolean, onClick: () -> Unit) {
    val colors = LocalModbusColors.current
    HeaderIconButton(
        stringResource(if (paused) R.string.cd_log_resume else R.string.cd_log_pause),
        onClick,
    ) {
        if (paused) PlayIcon(colors.accentText) else PauseIcon(colors.muted)
    }
}

@Composable
private fun FilterChips(
    filter: LogFilter,
    onFilter: (LogFilter) -> Unit,
    xs: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogFilter.entries.forEach { item ->
            SelectChip(
                text = stringResource(
                    when (item) {
                        LogFilter.ALL -> R.string.log_filter_all
                        LogFilter.LOCAL -> R.string.log_filter_local
                        LogFilter.FOREIGN -> R.string.log_filter_foreign
                        LogFilter.ECHO -> R.string.log_filter_echo
                        LogFilter.ERROR -> R.string.log_filter_error
                        LogFilter.UNKNOWN -> R.string.log_filter_unknown
                    },
                ),
                selected = item == filter,
                xs = xs,
                design = !xs,
                onClick = { onFilter(item) },
            )
        }
    }
}

/** 稿面日志容器：一张卡（圆角 16 + 边框），行间 1px rowLine，每行左侧 3px 语义色条。 */
@Composable
private fun LogCard(
    entries: List<LogEntry>,
    selected: Long?,
    paused: Boolean,
    crcDim: Boolean,
    emptyText: String,
    onSelect: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    val listState = rememberLazyListState()
    val lastSeq = entries.lastOrNull()?.seq
    LaunchedEffect(lastSeq, paused) {
        if (!paused && entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.line, shape),
    ) {
        if (entries.isEmpty()) {
            Text(emptyText, color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(0.dp)) {
                items(entries, key = { it.seq }) { entry ->
                    LogRow(entry, picked = entry.seq == selected, crcDim = crcDim, onClick = onSelect?.let { f -> { f(entry.seq) } })
                    if (entry !== entries.last()) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rowLine))
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, picked: Boolean, crcDim: Boolean, onClick: (() -> Unit)?) {
    val colors = LocalModbusColors.current
    val semantic = entry.semantic()
    val tone = colors.tone(semantic)
    val foreign = entry.event.kind == TrafficKind.RX_FOREIGN_REQUEST ||
        entry.event.kind == TrafficKind.RX_FOREIGN_RESPONSE
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (picked) colors.wash(colors.accent) else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(tone))
        Column(
            Modifier
                .weight(1f)
                .alpha(if (foreign && semantic != SemanticKind.ERROR) 0.6f else 1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatOffset(entry.offsetMs), style = Mono.copy(fontSize = 11.5.sp), color = colors.dim, maxLines = 1)
                SemanticBadge(semantic, entry.badgeText(entry.event.kind.label()))
                Text(
                    summaryText(entry.info, entry.event.kind),
                    color = colors.text,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.log_bytes, entry.info.size),
                    style = Mono.copy(fontSize = 11.5.sp),
                    color = colors.dim,
                    maxLines = 1,
                )
            }
            HexFrameText(
                entry.event.bytes,
                style = Mono.copy(fontSize = 12.5.sp),
                dimCrc = crcDim,
                modifier = Modifier.alpha(0.9f),
            )
        }
    }
}

/** 稿面“帧详情”：标题 + CRC 结论 + 复制 HEX；字段列表；脚注。 */
@Composable
private fun FrameDetailCard(entry: LogEntry?, modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    val clipboard = LocalClipboardManager.current
    var copied by remember(entry?.seq) { mutableStateOf(false) }
    Column(
        modifier
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.line, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (entry == null) {
            Text(stringResource(R.string.log_detail_title), color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.log_detail_empty), color = colors.muted, fontSize = 13.sp)
            return@Column
        }
        val info = entry.info
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.log_detail_title), color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            when (info.crcOk) {
                true -> SemanticBadge(SemanticKind.RX_LOCAL, stringResource(R.string.badge_crc_ok))
                false -> SemanticBadge(SemanticKind.ERROR, stringResource(R.string.badge_crc_bad))
                null -> Unit
            }
            Box(Modifier.weight(1f))
            val copiedText = stringResource(R.string.log_copied)
            TextLink(
                text = if (copied) copiedText else stringResource(R.string.log_copy_hex),
                onClick = {
                    clipboard.setText(AnnotatedString(entry.event.hex))
                    copied = true
                },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface2),
        ) {
            info.fields.forEachIndexed { index, field ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        field.hex,
                        style = Mono.copy(fontSize = 12.5.sp),
                        color = if (field.kind == FieldKind.CRC) colors.dim else colors.accentText,
                        modifier = Modifier.width(60.dp),
                    )
                    Text(fieldName(field, info), color = colors.text, fontSize = 12.5.sp, modifier = Modifier.width(64.dp))
                    Text(
                        fieldValue(field, info),
                        style = Mono.copy(fontSize = 12.5.sp),
                        color = if (field.kind == FieldKind.CRC && info.crcOk == false) colors.error else colors.text,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (index != info.fields.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rowLine))
                }
            }
        }
        Text(
            stringResource(R.string.log_detail_footer, info.size, formatOffset(entry.offsetMs)),
            color = colors.muted,
            fontSize = 11.5.sp,
        )
    }
}
