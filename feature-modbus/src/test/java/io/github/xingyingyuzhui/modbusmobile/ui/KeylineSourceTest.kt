package io.github.xingyingyuzhui.modbusmobile.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-20261018-and-16（CL-16）验收 A1：ui/ 下不再有 2dp 文字微调和 `pageStart() - ` 就地挪位（§13.2 / §13.8，AL-02 / AL-03）。
 * 纯 JVM：直接扫源码（单测工作目录是 app/），不受 B-3 影响。
 */
class KeylineSourceTest {
    private val uiDir = File("src/main/java/io/github/xingyingyuzhui/modbusmobile/ui")

    private fun hits(pattern: Regex): List<String> = uiDir.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file -> file.readLines().mapIndexedNotNull { i, line -> if (pattern.containsMatchIn(line)) "${file.name}:${i + 1}" else null } }
        .toList()

    @Test
    fun noTwoDpTextNudges() {
        assertTrue("找不到 ui 源码目录：${uiDir.absolutePath}", uiDir.isDirectory)
        assertEquals(emptyList<String>(), hits(Regex("""padding\((start|horizontal) = 2\.dp""")))
    }

    @Test
    fun noPageStartMinusNudges() {
        assertEquals(emptyList<String>(), hits(Regex("""pageStart\(\) - """)))
    }

    /** 05:51 追加的完成标准：v1 间距旧名已迁移并删除（按整词匹配，CurveMath 的 gapMs / gapStart 不是间距）。 */
    @Test
    fun noLegacySpacingNames() {
        val main = File("src/main/java")
        assertTrue("找不到 main 源码目录：${main.absolutePath}", main.isDirectory)
        val legacy = Regex("""\bgap[SM]\b""")
        val hits = main.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (legacy.containsMatchIn(l)) "${f.name}:${i + 1}" else null } }
            .toList()
        assertEquals(emptyList<String>(), hits)
    }
}
