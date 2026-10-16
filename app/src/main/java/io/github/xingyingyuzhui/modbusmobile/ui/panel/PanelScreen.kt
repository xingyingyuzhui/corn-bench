package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.ui.LocalProjectRepository
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.pageStart
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing

@Composable
fun PanelScreen() {
    val repository = LocalProjectRepository.current
    val model = viewModel { PanelViewModel(repository) }
    val state by model.state.collectAsStateWithLifecycle()
    PanelContent(state)
}

@Composable
fun PanelContent(state: PanelUiState) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val dock = LocalDockInsets.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = pageStart(), end = spacing.page)) {
            if (landscape) {
                // 稿面横屏：页头一行（面板 16/700），没有会话条，把高度还给卡片区（b4-3#1）。
                Text(
                    stringResource(R.string.title_panel),
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 2.dp),
                )
            } else {
                PageHeader(stringResource(R.string.title_panel))
                SessionStrip(Modifier.padding(bottom = spacing.gapS))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = pageStart(), end = spacing.page, bottom = dock.bottom + spacing.gapS),
            verticalArrangement = Arrangement.spacedBy(spacing.gapS),
        ) {
            when (state) {
                PanelUiState.Loading -> Unit
                is PanelUiState.Corrupt -> NoticeCard(
                    title = stringResource(R.string.panel_corrupt_title),
                    body = stringResource(
                        if (state.copySaved) R.string.panel_corrupt_body_saved else R.string.panel_corrupt_body,
                        state.reason,
                    ),
                    tag = "panel-corrupt",
                )
                is PanelUiState.Ready -> {
                    state.warnings.forEach { NoticeCard(title = null, body = it, tag = "panel-warning") }
                    val usable = state.project.panels.sumOf { p -> p.widgets.count { it.isUsable } }
                    if (usable == 0) {
                        NoticeCard(
                            title = stringResource(R.string.panel_empty_title),
                            body = stringResource(R.string.panel_empty_body),
                            tag = "panel-empty",
                        )
                    } else {
                        NoticeCard(
                            title = stringResource(R.string.panel_configured_title, usable),
                            body = stringResource(R.string.panel_configured_body),
                            tag = "panel-configured",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(title: String?, body: String, tag: String) {
    val colors = LocalModbusColors.current
    ModbusCard(
        Modifier.fillMaxWidth().testTag(tag),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (title != null) {
            Text(title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(body, color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
