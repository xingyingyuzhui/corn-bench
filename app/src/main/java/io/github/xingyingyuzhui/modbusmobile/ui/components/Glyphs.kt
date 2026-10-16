package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// 稿面里的 ⋮ ✎ ⟳ ⚙ ⚠ 是文字符号，不同系统字体里有的缺字、有的被渲染成彩色 emoji。
// 这里全部用 Canvas 画，Roborazzi 截图和真机一致。

@Composable
fun MoreIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension * 0.09f
        listOf(0.2f, 0.5f, 0.8f).forEach { y ->
            drawCircle(color, r, Offset(this.size.width / 2f, this.size.height * y))
        }
    }
}

@Composable
fun PencilIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = this.size.minDimension * 0.14f
        drawLine(color, Offset(w * 0.2f, h * 0.8f), Offset(w * 0.78f, h * 0.22f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.12f, h * 0.9f), Offset(w * 0.45f, h * 0.9f), stroke * 0.7f, StrokeCap.Round)
    }
}

@Composable
fun RefreshIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Canvas(modifier.size(size)) {
        val d = this.size.minDimension
        val stroke = d * 0.13f
        val radius = d * 0.34f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val start = -60f
        val sweep = 280f
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val theta = Math.toRadians((start + sweep).toDouble())
        val px = center.x + radius * cos(theta).toFloat()
        val py = center.y + radius * sin(theta).toFloat()
        val tx = -sin(theta).toFloat()
        val ty = cos(theta).toFloat()
        val nx = cos(theta).toFloat()
        val ny = sin(theta).toFloat()
        val head = d * 0.2f
        val path = Path().apply {
            moveTo(px + tx * head, py + ty * head)
            lineTo(px + nx * head, py + ny * head)
            lineTo(px - nx * head, py - ny * head)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun GearIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Canvas(modifier.size(size)) {
        val d = this.size.minDimension
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val stroke = d * 0.12f
        drawCircle(color, d * 0.2f, center, style = Stroke(width = stroke))
        repeat(8) { i ->
            val a = Math.toRadians(i * 45.0)
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            drawLine(
                color,
                Offset(center.x + c * d * 0.32f, center.y + s * d * 0.32f),
                Offset(center.x + c * d * 0.46f, center.y + s * d * 0.46f),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
fun WarnIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = this.size.minDimension * 0.1f
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.94f, h * 0.88f)
            lineTo(w * 0.06f, h * 0.88f)
            close()
        }
        drawPath(path, color, style = Stroke(width = stroke))
        drawLine(color, Offset(w * 0.5f, h * 0.38f), Offset(w * 0.5f, h * 0.62f), stroke, StrokeCap.Round)
        drawCircle(color, stroke * 0.7f, Offset(w * 0.5f, h * 0.76f))
    }
}

/** 折叠行尾的 › / ⌄。文字符号 ⌄ 在不少系统字体里缺字，所以用 Canvas 画。 */
@Composable
fun ChevronIcon(color: Color, expanded: Boolean, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = this.size.minDimension * 0.13f
        val path = Path().apply {
            if (expanded) {
                moveTo(w * 0.22f, h * 0.38f)
                lineTo(w * 0.5f, h * 0.66f)
                lineTo(w * 0.78f, h * 0.38f)
            } else {
                moveTo(w * 0.38f, h * 0.22f)
                lineTo(w * 0.66f, h * 0.5f)
                lineTo(w * 0.38f, h * 0.78f)
            }
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun PauseIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRect(color, Offset(w * 0.24f, h * 0.18f), Size(w * 0.17f, h * 0.64f))
        drawRect(color, Offset(w * 0.59f, h * 0.18f), Size(w * 0.17f, h * 0.64f))
    }
}

@Composable
fun PlayIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.15f)
            lineTo(w * 0.8f, h * 0.5f)
            lineTo(w * 0.28f, h * 0.85f)
            close()
        }
        drawPath(path, color)
    }
}
