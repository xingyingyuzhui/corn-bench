package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import io.github.xingyingyuzhui.modbusmobile.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmWarningTest {
    @Test
    fun coilExtraWarningFollowsTheSetting() {
        assertEquals(R.string.confirm_warning_coil, confirmWarningRes(broadcast = false, coil = true, coilExtra = true))
        assertEquals(R.string.confirm_warning, confirmWarningRes(broadcast = false, coil = true, coilExtra = false))
    }

    @Test
    fun registersNeverGetTheCoilWarning() {
        assertEquals(R.string.confirm_warning, confirmWarningRes(broadcast = false, coil = false, coilExtra = true))
    }

    @Test
    fun broadcastWarningIsAlwaysThereAndOnlyTheCoilSentenceIsOptional() {
        assertEquals(R.string.confirm_warning_broadcast_coil, confirmWarningRes(broadcast = true, coil = true, coilExtra = true))
        assertEquals(R.string.confirm_warning_broadcast, confirmWarningRes(broadcast = true, coil = true, coilExtra = false))
        assertEquals(R.string.confirm_warning_broadcast, confirmWarningRes(broadcast = true, coil = false, coilExtra = true))
    }
}
