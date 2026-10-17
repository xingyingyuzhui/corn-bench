package io.github.xingyingyuzhui.modbusmobile.usb

import io.github.xingyingyuzhui.modbusmobile.application.UsbStack
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import kotlinx.coroutines.CoroutineScope

/** Production builds expose physical USB devices only. */
@Suppress("UNUSED_PARAMETER")
internal fun withLocalSerialLab(stack: UsbStack, scope: CoroutineScope, clock: MonotonicClock): UsbStack = stack
