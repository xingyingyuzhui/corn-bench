package dev.corn.flashing.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File

/**
 * 像素基线截图（约定见 `docs/screenshots.md`）。图存 `src/test/screenshots/baseline/<name>.png` 并**入库**；
 * 其它截图只是“能渲染”检查，不入库。
 *
 * `-Pcorn.baseline=verify`：逐像素与入库基线比对，有差异或基线缺失即失败（CI 用）；
 * `-Pcorn.baseline=record`：重录基线；不传：跟随 Roborazzi 任务（record / verify / 普通 test 不截图）。
 * 与 :feature-modbus / :feature-flashing 各一份同样的 helper（测试代码不跨模块共享）。
 */
internal fun SemanticsNodeInteraction.captureBaseline(name: String) {
    val defaults = RoborazziOptions()
    val options = when (System.getProperty("corn.baseline")) {
        "verify" -> defaults.copy(taskType = RoborazziTaskType.Verify)
        "record" -> defaults.copy(taskType = RoborazziTaskType.Record)
        else -> defaults
    }
    // 用固定的 File 路径（相对模块根）：普通 test 任务不带 Roborazzi 的 outputDir，用字符串路径会落到 build/ 下。
    captureRoboImage(File("src/test/screenshots/baseline/$name.png"), roborazziOptions = options)
}
