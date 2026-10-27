package io.github.xingyingyuzhui.modbusmobile.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.Authorship
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors

/**
 * 打开作者的 GitHub 仓库。交给系统浏览器，应用本身仍不联网（没有 INTERNET 权限）；
 * 设备上没有浏览器时什么都不做，不崩。
 */
@Composable
internal fun rememberOpenRepo(): () -> Unit {
    val uri = LocalUriHandler.current
    return { runCatching { uri.openUri(Authorship.REPO_URL) } }
}

/** 设置列表最后一行的署名：“Corn Modbus · by xingyingyuzhui”，点一下打开仓库（热区 ≥ 48dp）。 */
@Composable
internal fun AuthorCredit(modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    val open = rememberOpenRepo()
    val label = stringResource(R.string.author_credit_cd)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("author-credit")
            .clickable(role = Role.Button, onClickLabel = label, onClick = open),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            buildAnnotatedString {
                append(Authorship.APP)
                append(" · by ")
                withStyle(SpanStyle(color = colors.accentText, textDecoration = TextDecoration.Underline)) { append(Authorship.AUTHOR) }
            },
            color = colors.dim,
            fontSize = 12.sp,
        )
    }
}
