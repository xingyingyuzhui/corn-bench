package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class Of(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val value: String) : UiText
    data class Bits(val start: Int, val values: List<Boolean>) : UiText
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Of -> stringResource(id, *args.toTypedArray())
    is UiText.Plain -> value
    is UiText.Bits -> values.mapIndexed { index, bit ->
        stringResource(
            io.github.xingyingyuzhui.modbusmobile.R.string.result_bit,
            start + index,
            stringResource(
                if (bit) {
                    io.github.xingyingyuzhui.modbusmobile.R.string.coil_on
                } else {
                    io.github.xingyingyuzhui.modbusmobile.R.string.coil_off
                },
            ),
        )
    }.joinToString("\n")
}
