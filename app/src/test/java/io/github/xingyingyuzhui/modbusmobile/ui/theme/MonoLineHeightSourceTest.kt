package io.github.xingyingyuzhui.modbusmobile.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-20261018-and-06 退回第 2 次 / qa-03 A3：`Mono` 现在是 monoBody（13/20），`Mono.copy(...)` 不写 lineHeight 就会继承 20sp 行高，
 * 小字号的报文行、时间行会变高。所以 main 下每个 `Mono.copy(` 调用（按括号配对，可以跨行）都必须带 `lineHeight`。
 * 纯 JVM：直接扫源码（单测工作目录是 app/），不受 B-3 影响。
 */
class MonoLineHeightSourceTest {
    private val main = File("src/main/java")

    @Test
    fun everyMonoCopySetsLineHeight() {
        assertTrue("找不到 main 源码目录：${main.absolutePath}", main.isDirectory)
        val missing = mutableListOf<String>()
        var calls = 0
        main.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            var from = 0
            while (true) {
                val at = text.indexOf("Mono.copy(", from)
                if (at < 0) break
                val open = at + "Mono.copy".length
                var depth = 0
                var end = open
                while (end < text.length) {
                    when (text[end]) {
                        '(' -> depth++
                        ')' -> if (--depth == 0) break
                    }
                    end++
                }
                calls++
                if (!text.substring(open, end).contains("lineHeight")) {
                    missing += "${file.name}:${text.substring(0, at).count { it == '\n' } + 1}"
                }
                from = end
            }
        }
        assertTrue("没扫到任何 Mono.copy(，扫描本身可能有问题", calls > 0)
        assertEquals(emptyList<String>(), missing)
    }
}
