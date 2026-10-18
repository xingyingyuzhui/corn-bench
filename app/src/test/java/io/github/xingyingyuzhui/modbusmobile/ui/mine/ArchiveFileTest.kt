package io.github.xingyingyuzhui.modbusmobile.ui.mine

import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveFileTest {
    @Test
    fun failedAndCancelledExportsRemovePartialFiles() = runTest {
        val dir = Files.createTempDirectory("archive-test").toFile()
        try {
            for (error in listOf(IOException("disk full"), CancellationException("cancel"))) {
                val result = runCatching {
                    buildArchiveFile(dir, System.currentTimeMillis()) {
                        it.write(byteArrayOf(1, 2, 3))
                        throw error
                    }
                }
                assertTrue(result.exceptionOrNull() === error)
                assertTrue(dir.listFiles().orEmpty().isEmpty())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun successfulExportKeepsOtherRecentlySharedFiles() = runTest {
        val dir = Files.createTempDirectory("archive-test").toFile()
        try {
            val first = buildArchiveFile(dir, System.currentTimeMillis()) { it.write(1) }
            val second = buildArchiveFile(dir, System.currentTimeMillis()) { it.write(2) }
            assertTrue(first.exists())
            assertEquals(listOf<Byte>(2), second.readBytes().toList())
        } finally {
            dir.deleteRecursively()
        }
    }
}
