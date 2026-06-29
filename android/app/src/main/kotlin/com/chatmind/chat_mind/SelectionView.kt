package com.chatmind.chat_mind

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SelectionView(context: Context) : View(context) {

    var onRectChanged: ((Int, Int, Int, Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 选区矩形（相对于屏幕的坐标）
    private var rect = RectF(0f, 0f, 0f, 0f)
    private var minSize = 100f
    private var handleSize = 40f

    // 屏幕尺寸
    private var screenWidth = 0
    private var screenHeight = 0

    private var dragMode = DragMode.NONE
    private var startX = 0f
    private var startY = 0f
    private var startRect = RectF()

    private enum class DragMode {
        NONE, MOVE, RESIZE_LEFT, RESIZE_RIGHT, RESIZE_TOP, RESIZE_BOTTOM,
        RESIZE_LT, RESIZE_RT, RESIZE_LB, RESIZE_RB
    }

    init {
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 4f
        borderPaint.color = Color.parseColor("#667EEA")

        handlePaint.color = Color.parseColor("#667EEA")

        dimPaint.color = Color.parseColor("#CC000000")

        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    fun setInitialRect(left: Int, top: Int, right: Int, bottom: Int) {
        rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        invalidate()
        notifyRectChanged()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制暗色背景（全屏减去选区）
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top, dimPaint)
        canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, dimPaint)
        canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, dimPaint)

        // 绘制发光边框
        borderPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.OUTER)
        borderPaint.color = Color.parseColor("#667EEA")
        canvas.drawRect(rect, borderPaint)

        borderPaint.maskFilter = null
        borderPaint.color = Color.parseColor("#764BA2")
        canvas.drawRect(rect, borderPaint)

        // 绘制四角手柄
        val hs = handleSize / 2f
        drawHandle(canvas, rect.left, rect.top, hs)
        drawHandle(canvas, rect.right, rect.top, hs)
        drawHandle(canvas, rect.left, rect.bottom, hs)
        drawHandle(canvas, rect.right, rect.bottom, hs)
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        canvas.drawCircle(cx, cy, radius, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                startRect.set(rect)
                dragMode = getDragMode(event.x, event.y)
                return dragMode != DragMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY
                when (dragMode) {
                    DragMode.MOVE -> {
                        rect.offset(dx, dy)
                        // 限制在屏幕内
                        if (rect.left < 0) rect.offset(-rect.left, 0f)
                        if (rect.top < 0) rect.offset(0f, -rect.top)
                        if (rect.right > screenWidth) rect.offset(screenWidth - rect.right, 0f)
                        if (rect.bottom > screenHeight) rect.offset(0f, screenHeight - rect.bottom)
                        startX = event.x
                        startY = event.y
                    }
                    DragMode.RESIZE_LEFT -> {
                        rect.left = min(startRect.left + dx, rect.right - minSize)
                    }
                    DragMode.RESIZE_RIGHT -> {
                        rect.right = max(startRect.right + dx, rect.left + minSize)
                    }
                    DragMode.RESIZE_TOP -> {
                        rect.top = min(startRect.top + dy, rect.bottom - minSize)
                    }
                    DragMode.RESIZE_BOTTOM -> {
                        rect.bottom = max(startRect.bottom + dy, rect.top + minSize)
                    }
                    DragMode.RESIZE_LT -> {
                        rect.left = min(startRect.left + dx, rect.right - minSize)
                        rect.top = min(startRect.top + dy, rect.bottom - minSize)
                    }
                    DragMode.RESIZE_RT -> {
                        rect.right = max(startRect.right + dx, rect.left + minSize)
                        rect.top = min(startRect.top + dy, rect.bottom - minSize)
                    }
                    DragMode.RESIZE_LB -> {
                        rect.left = min(startRect.left + dx, rect.right - minSize)
                        rect.bottom = max(startRect.bottom + dy, rect.top + minSize)
                    }
                    DragMode.RESIZE_RB -> {
                        rect.right = max(startRect.right + dx, rect.left + minSize)
                        rect.bottom = max(startRect.bottom + dy, rect.top + minSize)
                    }
                    else -> {}
                }
                invalidate()
                notifyRectChanged()
                return true
            }
            MotionEvent.ACTION_UP -> {
                dragMode = DragMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getDragMode(x: Float, y: Float): DragMode {
        val threshold = handleSize + 10
        val onLeft = abs(x - rect.left) < threshold
        val onRight = abs(x - rect.right) < threshold
        val onTop = abs(y - rect.top) < threshold
        val onBottom = abs(y - rect.bottom) < threshold

        return when {
            onLeft && onTop -> DragMode.RESIZE_LT
            onRight && onTop -> DragMode.RESIZE_RT
            onLeft && onBottom -> DragMode.RESIZE_LB
            onRight && onBottom -> DragMode.RESIZE_RB
            onLeft -> DragMode.RESIZE_LEFT
            onRight -> DragMode.RESIZE_RIGHT
            onTop -> DragMode.RESIZE_TOP
            onBottom -> DragMode.RESIZE_BOTTOM
            rect.contains(x, y) -> DragMode.MOVE
            else -> DragMode.NONE
        }
    }

    private fun notifyRectChanged() {
        onRectChanged?.invoke(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt()
        )
    }
}
