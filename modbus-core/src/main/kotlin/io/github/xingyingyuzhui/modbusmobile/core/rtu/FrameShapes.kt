package io.github.xingyingyuzhui.modbusmobile.core.rtu

interface RxWindow {
    val size: Int
    operator fun get(index: Int): Int
}

/**
 * 环形接收缓冲。下标 0 是最老的字节，[get] 返回 0..255。
 */
class RxBuffer(val capacity: Int) : RxWindow {
    private val data = ByteArray(capacity)
    private var head = 0
    private var count = 0

    override val size: Int get() = count

    override fun get(index: Int): Int {
        if (index !in 0 until count) throw IndexOutOfBoundsException("index=$index size=$count")
        return data[(head + index) % data.size].toInt() and 0xFF
    }

    fun drop(n: Int) {
        val removed = n.coerceIn(0, count)
        head = (head + removed) % data.size
        count -= removed
        if (count == 0) head = 0
    }

    fun copy(from: Int, len: Int): ByteArray {
        require(from >= 0 && len >= 0 && from + len <= count)
        return ByteArray(len) { index -> data[(head + from + index) % data.size] }
    }

    fun append(bytes: ByteArray) {
        require(bytes.size <= data.size) { "单次追加不能超过缓冲容量" }
        val overflow = count + bytes.size - data.size
        if (overflow > 0) drop(overflow)
        for (byte in bytes) {
            data[(head + count) % data.size] = byte
            count += 1
        }
    }

    fun clear() {
        head = 0
        count = 0
    }
}

sealed interface Shape {
    data class Length(val total: Int) : Shape
    data class NeedMore(val atLeast: Int) : Shape
    data object Unmodeled : Shape
    data object Malformed : Shape
}

object FrameShapes {
    const val MAX_ADU = 256

    fun request(buf: RxWindow): Shape {
        if (buf.size < 2) return Shape.NeedMore(2)
        return when (buf[1]) {
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06 -> Shape.Length(8)
            0x0F, 0x10 -> counted(buf, byteCountAt = 6, overhead = 9)
            0x17 -> counted(buf, byteCountAt = 10, overhead = 13)
            0x07, 0x0B, 0x0C, 0x11 -> Shape.Length(4)
            0x16 -> Shape.Length(10)
            0x18 -> Shape.Length(6)
            else -> Shape.Unmodeled
        }
    }

    /** [fc] 是原请求功能码。异常响应固定 5 字节。 */
    fun response(fc: Int, buf: RxWindow): Shape {
        if (buf.size < 2) return Shape.NeedMore(2)
        if (buf[1] == (fc or 0x80)) return Shape.Length(5)
        if (buf[1] != fc) return Shape.Malformed
        return when (fc) {
            0x01, 0x02, 0x03, 0x04, 0x17, 0x0C, 0x11 -> counted(buf, byteCountAt = 2, overhead = 5)
            0x05, 0x06, 0x0F, 0x10, 0x0B -> Shape.Length(8)
            0x07 -> Shape.Length(5)
            0x16 -> Shape.Length(10)
            0x18 -> counted16(buf, at = 2, overhead = 6)
            else -> Shape.Unmodeled
        }
    }

    private fun counted(buf: RxWindow, byteCountAt: Int, overhead: Int): Shape {
        if (buf.size <= byteCountAt) return Shape.NeedMore(byteCountAt + 1)
        val total = overhead + buf[byteCountAt]
        return if (total > MAX_ADU) Shape.Malformed else Shape.Length(total)
    }

    private fun counted16(buf: RxWindow, at: Int, overhead: Int): Shape {
        if (buf.size < at + 2) return Shape.NeedMore(at + 2)
        val byteCount = (buf[at] shl 8) or buf[at + 1]
        val total = overhead.toLong() + byteCount
        return if (total > MAX_ADU) Shape.Malformed else Shape.Length(total.toInt())
    }
}
