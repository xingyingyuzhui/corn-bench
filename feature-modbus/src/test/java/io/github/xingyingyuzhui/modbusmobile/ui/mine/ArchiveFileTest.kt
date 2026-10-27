package io.github.xingyingyuzhui.modbusmobile.ui.mine

import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveFileTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun parentCancellationAfterBlockingIoCompletesDeletesUndeliveredArchive() = runTest {
        val dir = Files.createTempDirectory("archive-test").toFile()
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        var handedOff = false
        try {
            val job = launch {
                withOwnedArchive(create = {
                    val file = buildArchiveFile(dir, System.currentTimeMillis()) { it.write(42) }
                    ready.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    file
                }, dispatcher = dispatcher) { handedOff = true }
            }
            runCurrent()
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            job.cancel()
            release.countDown()
            job.join()
            assertTrue(!handedOff)
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally {
            release.countDown()
            dispatcher.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun failedShareHandoffDeletesCompletedArchive() = runTest {
        val dir = Files.createTempDirectory("archive-test").toFile()
        try {
            val error = IllegalArgumentException("provider rejected path")
            val result = runCatching {
                withOwnedArchive({ buildArchiveFile(dir, System.currentTimeMillis()) { it.write(42) } }) {
                    throw error
                }
            }
            assertTrue(result.exceptionOrNull() === error)
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

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
