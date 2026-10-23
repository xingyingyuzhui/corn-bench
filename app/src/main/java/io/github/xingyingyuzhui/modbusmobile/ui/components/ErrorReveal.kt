@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * 一次“提交没通过”：第一个带错误的输入框把自己滚进可见区域。
 * 长表单里错误常在屏幕外，按钮按下去却什么都看不到，像是没反应。
 * 只有第一个（组合顺序 = 从上到下）认领，所以停在最上面的错误。
 */
@Stable
class ErrorReveal internal constructor(val attempt: Int) {
    internal var claimed = false
}

val LocalErrorReveal = compositionLocalOf<ErrorReveal?> { null }

/** [attempt] 每次提交失败加一；0 表示还没提交过，不滚动。 */
@Composable
fun rememberErrorReveal(attempt: Int): ErrorReveal? = remember(attempt) { if (attempt > 0) ErrorReveal(attempt) else null }

fun Modifier.revealOnError(isError: Boolean): Modifier = composed {
    val reveal = LocalErrorReveal.current
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(reveal) {
        if (reveal != null && isError && !reveal.claimed) {
            reveal.claimed = true
            requester.bringIntoView()
        }
    }
    bringIntoViewRequester(requester)
}
