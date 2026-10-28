package dev.corn.flashing

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 烧录模式已并进 Corn Bench：界面、通知、剪贴板、导出文件名里不再出现独立应用名“Corn Flashing”。
 * 只扫字符串字面量与资源值，注释里写历史来源（“原 Corn Flashing …”）不算。
 */
class UserVisibleBrandingTest {
    private val legacyName = Regex("""Corn[ -]?Flashing""", RegexOption.IGNORE_CASE)
    private val literal = Regex(""""(?:[^"\\\n]|\\.)*"""")

    @Test
    fun noLegacyAppNameInStringLiterals() {
        val main = File("src/main")
        assertTrue("工作目录应为模块根：${File(".").absolutePath}", main.isDirectory)
        val hits = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filterNot { (_, line) -> line.trimStart().let { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") } }
                    .flatMap { (i, line) ->
                        literal.findAll(line.substringBefore(" // ")).map { it.value }
                            .filter { legacyName.containsMatchIn(it) && !it.contains(".db\"") }
                            .map { "${file.path}:${i + 1}: $it" }
                    }
            }
            .toList()
        assertTrue("用户可见文案仍有旧应用名：\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test
    fun noLegacyAppNameInResourceValues() {
        val hits = File("src/main/res").walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .flatMap { file ->
                // 去掉 XML 注释后再查：注释里写来源可以。
                val text = file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
                if (legacyName.containsMatchIn(text)) sequenceOf(file.path) else emptySequence()
            }
            .toList()
        assertTrue("资源里仍有旧应用名：$hits", hits.isEmpty())
    }
}
