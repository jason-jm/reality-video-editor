package com.example.prismstudio.p40b43a88.ui.edit.components

import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.model.ZoomDirection
import com.example.prismstudio.p40b43a88.domain.model.ZoomEase
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusX
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusY
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** The inspector's Dynamic Zoom boxes and the sentence under them. */
class ZoomFramingTest {

    private val push = DynamicZoom(ZoomDirection.IN, 20, ZoomEase.LINEAR, ZoomFocusX.RIGHT, ZoomFocusY.TOP)

    @Test
    fun `the boxes frame what the first and last frames show`() {
        assertArrayEquals("the whole picture first", floatArrayOf(0f, 0f, 1f, 1f), zoomFrame(push, last = false), 1e-6f)
        // 1/1.2 of the picture, against its top-right corner.
        assertArrayEquals(floatArrayOf(1f / 6, 0f, 1f, 5f / 6), zoomFrame(push, last = true), 1e-6f)
        val pan = DynamicZoom(ZoomDirection.PAN, 20, ZoomEase.LINEAR, ZoomFocusX.RIGHT)
        assertArrayEquals("against the left edge", floatArrayOf(0f, 1f / 12, 5f / 6, 11f / 12), zoomFrame(pan, last = false), 1e-6f)
        assertArrayEquals("then the right", floatArrayOf(1f / 6, 1f / 12, 1f, 11f / 12), zoomFrame(pan, last = true), 1e-6f)
    }

    @Test
    fun `the note says where the move goes`() {
        assertEquals("从全画面推近到右上角，放大 20%", zoomMotionText(push))
        assertEquals("从中心（放大 30%）拉远到全画面", zoomMotionText(DynamicZoom(ZoomDirection.OUT, 30)))
        assertEquals("放大 20%，从左下角平移到右上角", zoomMotionText(push.copy(direction = ZoomDirection.PAN)))
        assertEquals("放大 20%，从上方平移到下方", zoomMotionText(DynamicZoom(ZoomDirection.PAN, 20, focusY = ZoomFocusY.BOTTOM)))
        assertEquals("放大 20%，水平和垂直都在中间时画面不移动", zoomMotionText(DynamicZoom(ZoomDirection.PAN, 20)))
    }
}
