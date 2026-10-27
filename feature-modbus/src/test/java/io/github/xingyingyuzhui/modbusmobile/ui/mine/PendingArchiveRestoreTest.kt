package io.github.xingyingyuzhui.modbusmobile.ui.mine

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PendingArchiveRestoreTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pendingDocumentPickerSurvivesSavedStateRestoreAndCancelDeletesFile() {
        val dir = Files.createTempDirectory("pending-export").toFile()
        val restoration = StateRestorationTester(compose)
        lateinit var owner: PendingArchive
        restoration.setContent { owner = rememberPendingArchive(dir) }
        try {
            val first = File(dir, "modbus-export-save.zip").apply { writeText("archive") }
            compose.runOnIdle { owner.replace(first) }
            restoration.emulateSavedInstanceStateRestore()
            compose.runOnIdle {
                assertEquals(first.canonicalFile, owner.take())
                owner.replace(first)
            }
            restoration.emulateSavedInstanceStateRestore()
            compose.runOnIdle {
                owner.cancel()
                assertTrue(!first.exists())
                assertNull(owner.take())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun restoredOutsideOrMissingPathsAreRejectedWithoutDeletingOutsideFiles() {
        val dir = Files.createTempDirectory("pending-export").toFile()
        val outside = File.createTempFile("modbus-export-", ".zip")
        try {
            var path: String? = outside.path
            val pending = PendingArchive(dir, { path }, { path = it })
            pending.cancel()
            assertTrue(outside.exists())
            assertNull(path)
            path = File(dir, "modbus-export-missing.zip").path
            assertNull(pending.take())
            assertNull(path)
        } finally {
            outside.delete()
            dir.deleteRecursively()
        }
    }
}
