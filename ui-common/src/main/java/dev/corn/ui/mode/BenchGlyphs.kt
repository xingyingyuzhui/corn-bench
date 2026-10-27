package dev.corn.ui.mode

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 外壳用的几个线性图标（24 网格、2px 描边，风格与 Flashing 的 Lucide 图标一致）。 */
object BenchGlyphs {
    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    /** 上位机：显示器 + 命令行提示符。 */
    val Terminal: ImageVector by lazy {
        icon("terminal") {
            moveTo(4f, 4f); lineTo(20f, 4f); quadTo(22f, 4f, 22f, 6f); lineTo(22f, 16f); quadTo(22f, 18f, 20f, 18f)
            lineTo(4f, 18f); quadTo(2f, 18f, 2f, 16f); lineTo(2f, 6f); quadTo(2f, 4f, 4f, 4f); close()
            moveTo(6f, 9f); lineTo(9f, 11f); lineTo(6f, 13f)
            moveTo(11f, 14f); lineTo(15f, 14f)
            moveTo(8f, 22f); lineTo(16f, 22f)
            moveTo(12f, 18f); lineTo(12f, 22f)
        }
    }

    /** 烧录：芯片。 */
    val Chip: ImageVector by lazy {
        icon("chip") {
            moveTo(7f, 5f); lineTo(17f, 5f); quadTo(19f, 5f, 19f, 7f); lineTo(19f, 17f); quadTo(19f, 19f, 17f, 19f)
            lineTo(7f, 19f); quadTo(5f, 19f, 5f, 17f); lineTo(5f, 7f); quadTo(5f, 5f, 7f, 5f); close()
            moveTo(9f, 9f); lineTo(15f, 9f); lineTo(15f, 15f); lineTo(9f, 15f); close()
            moveTo(9f, 2f); lineTo(9f, 5f); moveTo(15f, 2f); lineTo(15f, 5f)
            moveTo(9f, 19f); lineTo(9f, 22f); moveTo(15f, 19f); lineTo(15f, 22f)
            moveTo(2f, 9f); lineTo(5f, 9f); moveTo(2f, 15f); lineTo(5f, 15f)
            moveTo(19f, 9f); lineTo(22f, 9f); moveTo(19f, 15f); lineTo(22f, 15f)
        }
    }

    val Lock: ImageVector by lazy {
        icon("lock") {
            moveTo(5f, 11f); lineTo(19f, 11f); lineTo(19f, 21f); lineTo(5f, 21f); close()
            moveTo(8f, 11f); lineTo(8f, 7f); quadTo(8f, 3f, 12f, 3f); quadTo(16f, 3f, 16f, 7f); lineTo(16f, 11f)
        }
    }

    val Usb: ImageVector by lazy {
        icon("usb") {
            moveTo(12f, 2f); lineTo(12f, 17f)
            moveTo(9f, 5f); lineTo(12f, 2f); lineTo(15f, 5f)
            moveTo(12f, 13f); lineTo(7f, 10f); lineTo(7f, 7f)
            moveTo(12f, 15f); lineTo(17f, 12f); lineTo(17f, 9f)
            moveTo(10f, 19f); quadTo(10f, 17f, 12f, 17f); quadTo(14f, 17f, 14f, 19f); quadTo(14f, 21f, 12f, 21f); quadTo(10f, 21f, 10f, 19f); close()
        }
    }
}
