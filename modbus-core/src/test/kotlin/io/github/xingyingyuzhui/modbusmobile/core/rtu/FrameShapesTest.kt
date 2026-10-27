package io.github.xingyingyuzhui.modbusmobile.core.rtu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FrameShapesTest {
    @Test
    fun requestLengthForEachModeledFunction() {
        assertEquals(Shape.Length(8), FrameShapes.request(window(0x01, 0x01, 0, 0, 0, 1, 0, 0)))
        assertEquals(Shape.Length(8), FrameShapes.request(window(0x01, 0x02, 0, 0, 0, 1)))
        assertEquals(Shape.Length(8), FrameShapes.request(window(0x01, 0x03, 0, 0, 0, 1)))
        assertEquals(Shape.Length(8), FrameShapes.request(window(0x01, 0x04, 0, 0, 0, 1)))
        assertEquals(Shape.Length(8), FrameShapes.request(window(0x01, 0x05, 0, 0, 0xFF, 0)))
        assertEquals(Shape.Length(8), FrameShapes.request(window(0x01, 0x06, 0, 0, 0, 1)))
        assertEquals(Shape.Length(4), FrameShapes.request(window(0x01, 0x07, 0, 0)))
        assertEquals(Shape.Length(4), FrameShapes.request(window(0x01, 0x0B, 0, 0)))
        assertEquals(Shape.Length(4), FrameShapes.request(window(0x01, 0x0C, 0, 0)))
        assertEquals(Shape.Length(11), FrameShapes.request(window(0x01, 0x0F, 0, 0, 0, 8, 2, 0, 0)))
        assertEquals(Shape.Length(13), FrameShapes.request(window(0x01, 0x10, 0, 0, 0, 2, 4, 0, 1, 0, 2)))
        assertEquals(Shape.Length(4), FrameShapes.request(window(0x01, 0x11, 0, 0)))
        assertEquals(Shape.Length(10), FrameShapes.request(window(0x01, 0x16, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals(Shape.Length(15), FrameShapes.request(window(0x01, 0x17, 0, 0, 0, 1, 0, 0, 0, 1, 2, 0, 1)))
        assertEquals(Shape.Length(6), FrameShapes.request(window(0x01, 0x18, 0, 0, 0, 0)))
        assertEquals(Shape.Unmodeled, FrameShapes.request(window(0x01, 0x41, 0xAA, 0xBB)))
        assertEquals(Shape.NeedMore(2), FrameShapes.request(window(0x01)))
        assertIs<Shape.Malformed>(FrameShapes.request(window(0x01, 0x10, 0, 0, 0, 1, 248)))
    }

    @Test
    fun responseLengthForEachModeledFunction() {
        assertEquals(Shape.Length(7), FrameShapes.response(0x01, window(0x01, 0x01, 2, 0, 0)))
        assertEquals(Shape.Length(6), FrameShapes.response(0x02, window(0x01, 0x02, 1, 0)))
        assertEquals(Shape.Length(9), FrameShapes.response(0x03, window(0x01, 0x03, 4, 0, 1, 0, 2)))
        assertEquals(Shape.Length(7), FrameShapes.response(0x04, window(0x01, 0x04, 2, 0, 1)))
        assertEquals(Shape.Length(8), FrameShapes.response(0x05, window(0x01, 0x05, 0, 0, 0xFF, 0, 0, 0)))
        assertEquals(Shape.Length(8), FrameShapes.response(0x06, window(0x01, 0x06, 0, 0, 0, 1, 0, 0)))
        assertEquals(Shape.Length(5), FrameShapes.response(0x07, window(0x01, 0x07, 0, 0, 0)))
        assertEquals(Shape.Length(8), FrameShapes.response(0x0B, window(0x01, 0x0B, 0, 0, 0, 0, 0, 0)))
        assertEquals(Shape.Length(6), FrameShapes.response(0x0C, window(0x01, 0x0C, 1, 0)))
        assertEquals(Shape.Length(8), FrameShapes.response(0x0F, window(0x01, 0x0F, 0, 0, 0, 1, 0, 0)))
        assertEquals(Shape.Length(8), FrameShapes.response(0x10, window(0x01, 0x10, 0, 0, 0, 1, 0, 0)))
        assertEquals(Shape.Length(8), FrameShapes.response(0x11, window(0x01, 0x11, 3, 1, 2, 3)))
        assertEquals(Shape.Length(10), FrameShapes.response(0x16, window(0x01, 0x16, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals(Shape.Length(8), FrameShapes.response(0x17, window(0x01, 0x17, 3, 0, 1, 2)))
        assertEquals(Shape.Length(10), FrameShapes.response(0x18, window(0x01, 0x18, 0x00, 0x04, 0, 1, 0, 2)))
        assertEquals(Shape.Length(5), FrameShapes.response(0x03, window(0x01, 0x83, 0x02)))
        assertEquals(Shape.Length(5), FrameShapes.response(0x17, window(0x01, 0x97, 0x01)))
        assertIs<Shape.Malformed>(FrameShapes.response(0x03, window(0x01, 0x06, 0, 0)))
        assertEquals(Shape.Unmodeled, FrameShapes.response(0x41, window(0x01, 0x41, 0, 0)))
        assertIs<Shape.Malformed>(FrameShapes.response(0x18, window(0x01, 0x18, 0x01, 0x00)))
        assertEquals(Shape.NeedMore(4), FrameShapes.response(0x18, window(0x01, 0x18, 0x00)))
    }
}

private fun window(vararg bytes: Int) = object : RxWindow {
    override val size: Int = bytes.size
    override fun get(index: Int): Int = bytes[index]
}
