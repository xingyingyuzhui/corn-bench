package dev.corn.ui.mode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.corn.ui.theme.BenchRadius
import dev.corn.ui.theme.BenchSize
import dev.corn.ui.theme.BenchType
import dev.corn.ui.theme.LocalBenchColors
import dev.corn.ui.theme.LocalBenchSpacing

/** 两种模式。持久化用 [key]，不要用 ordinal。 */
enum class BenchMode(val key: String, val title: String, val subtitle: String) {
    MODBUS("modbus", "上位机", "Modbus RTU 主站 / 从站 · 寄存器读写 · 面板与日志"),
    FLASHING("flashing", "烧录", "ST-LINK · STM32 离线烧录 · 单片与批量"),
    ;

    val icon: ImageVector get() = when (this) {
        MODBUS -> BenchGlyphs.Terminal
        FLASHING -> BenchGlyphs.Chip
    }

    companion object {
        fun fromKey(key: String?): BenchMode? = entries.firstOrNull { it.key == key }
    }
}

/** 面板里一张模式卡的状态。[blockedReason] 非空时卡片置灰并写明原因，不可点。 */
data class ModeOption(
    val mode: BenchMode,
    val current: Boolean,
    val blockedReason: String? = null,
)

fun modeCardTag(mode: BenchMode) = "mode-card-${mode.key}"

const val ModeSheetTag = "mode-sheet"
const val ModeConfirmTag = "mode-confirm"
const val ModePickerTag = "mode-picker"

/** 一张模式大卡：图标块 + 标题 + 说明；当前模式右上角标「当前」。 */
@Composable
fun ModeCard(option: ModeOption, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBenchColors.current
    val shape = RoundedCornerShape(BenchRadius.card)
    val blocked = option.blockedReason != null
    val clickable = !blocked && !option.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BenchSize.modeCard)
            .clip(shape)
            .background(if (option.current) colors.accentContainer else colors.surface1)
            .border(if (option.current) 1.5.dp else 1.dp, if (option.current) colors.accentBorder else colors.line, shape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                selected = option.current
                if (!clickable) disabled()
            }
            .clickable(enabled = clickable, onClick = onClick)
            .testTag(modeCardTag(option.mode))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val contentAlpha = if (blocked) 0.45f else 1f
        Box(
            Modifier.size(52.dp).alpha(contentAlpha).clip(RoundedCornerShape(BenchRadius.m))
                .background(if (option.current) colors.accent else colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(option.mode.icon, null, Modifier.size(26.dp), tint = if (option.current) colors.onAccent else colors.accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(option.mode.title, color = colors.text.copy(alpha = contentAlpha), style = BenchType.title)
                if (option.current) {
                    Text(
                        "当前",
                        color = colors.onAccent,
                        style = BenchType.label,
                        modifier = Modifier.clip(RoundedCornerShape(BenchRadius.pill)).background(colors.accent)
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
            Text(option.mode.subtitle, color = colors.muted.copy(alpha = contentAlpha), style = BenchType.caption)
            option.blockedReason?.let {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(BenchGlyphs.Lock, null, Modifier.padding(top = 2.dp).size(12.dp), tint = colors.warning)
                    Text(it, color = colors.warning, style = BenchType.caption)
                }
            }
        }
    }
}

/** 页内遮罩：点遮罩 = 关闭。遮罩本身吃掉点击，不漏到下层页面。 */
@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val colors = LocalBenchColors.current
    Box(
        Modifier.fillMaxSize().background(colors.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        content = content,
    )
}

/** 拦住面板内部的点击，不让它冒到遮罩上把面板关掉（不画水波纹、不进无障碍树）。 */
private fun Modifier.swallowClicks(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/**
 * 模式切换底部面板：两张大卡（上位机 / 烧录），当前模式标「当前」，被拦截的模式置灰写原因。
 * 页内浮层（不是系统对话框），返回键 / 点遮罩关闭。
 */
@Composable
fun ModeSwitchSheet(
    options: List<ModeOption>,
    onPick: (BenchMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBenchColors.current
    val spacing = LocalBenchSpacing.current
    BackHandler(onBack = onDismiss)
    Scrim(onDismiss) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = BenchRadius.sheet, topEnd = BenchRadius.sheet))
                .background(colors.surface1)
                .swallowClicks()
                .testTag(ModeSheetTag)
                .navigationBarsPadding()
                .padding(start = spacing.page, end = spacing.page, bottom = spacing.page),
            verticalArrangement = Arrangement.spacedBy(spacing.group),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(BenchRadius.pill)).background(colors.surface3))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("切换模式", color = colors.text, style = BenchType.headline)
                Text("同一时刻只运行一个模式，切换后另一个模式的 USB 连接会释放", color = colors.muted, style = BenchType.caption)
            }
            options.forEach { option -> ModeCard(option, onClick = { onPick(option.mode) }) }
        }
    }
}

/**
 * 页内确认框（不用系统 Dialog：截图测试里也能渲染，和 Modbus 的页内浮层规则一致）。
 * [danger] 为 true 时确认按钮用危险色。
 */
@Composable
fun ModeConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "取消",
    danger: Boolean = true,
) {
    val colors = LocalBenchColors.current
    BackHandler(onBack = onDismiss)
    Scrim(onDismiss) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(BenchRadius.xl))
                .background(colors.surface1)
                .border(1.dp, colors.line, RoundedCornerShape(BenchRadius.xl))
                .swallowClicks()
                .testTag(ModeConfirmTag)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = colors.text, style = BenchType.title)
            Text(message, color = colors.muted, style = BenchType.body)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton(dismissLabel, colors.surface2, colors.text, Modifier.weight(1f), onDismiss)
                DialogButton(
                    confirmLabel,
                    if (danger) colors.dangerFill else colors.accent,
                    if (danger) colors.onDanger else colors.onAccent,
                    Modifier.weight(1f),
                    onConfirm,
                )
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(BenchSize.button).clip(RoundedCornerShape(BenchRadius.button)).background(bg)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, style = BenchType.button, maxLines = 1) }
}

/** 首次启动的全屏模式选择页（只出现一次；选完写入 last_mode）。 */
@Composable
fun ModePickerScreen(onPick: (BenchMode) -> Unit) {
    val colors = LocalBenchColors.current
    val spacing = LocalBenchSpacing.current
    Box(Modifier.fillMaxSize().background(colors.bg).testTag(ModePickerTag), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = spacing.page, vertical = spacing.section),
            verticalArrangement = Arrangement.spacedBy(spacing.group),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CornMark(size = 44.dp)
                Column {
                    Text("Corn Bench", color = colors.text, style = BenchType.display)
                    Text("上位机 + 离线烧录", color = colors.muted, style = BenchType.caption)
                }
            }
            Spacer(Modifier.height(spacing.s))
            Text("先选一个模式", color = colors.text, style = BenchType.headline)
            Text(
                "之后随时可以点页面左上角的玉米图标切换。同一时刻只运行一个模式。",
                color = colors.muted,
                style = BenchType.body,
            )
            BenchMode.entries.forEach { mode -> ModeCard(ModeOption(mode, current = false), onClick = { onPick(mode) }) }
            Text(
                "插入 USB 设备不会自动切换模式",
                color = colors.dim,
                style = BenchType.caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.s),
            )
        }
    }
}
