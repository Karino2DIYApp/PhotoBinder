package io.github.karino2.photobinder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    val points = mutableListOf<PointF>()
    var onPointsChanged: ((Int) -> Unit)? = null
    
    private var draggedPointIndex = -1
    private val touchSlop = 40f

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.FILL
    }
    private val linePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (point in points) {
            canvas.drawCircle(point.x, point.y, 20f, paint)
        }
        if (points.size == 4) {
            canvas.drawLine(points[0].x, points[0].y, points[1].x, points[1].y, linePaint)
            canvas.drawLine(points[1].x, points[1].y, points[2].x, points[2].y, linePaint)
            canvas.drawLine(points[2].x, points[2].y, points[3].x, points[3].y, linePaint)
            canvas.drawLine(points[3].x, points[3].y, points[0].x, points[0].y, linePaint)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggedPointIndex = points.indexOfFirst { hypot(it.x - x, it.y - y) < touchSlop }
                if (draggedPointIndex == -1) {
                    if (points.size < 4) {
                        points.add(PointF(x, y))
                        onPointsChanged?.invoke(points.size)
                        invalidate()
                    } else {
                        // All 4 points exist, move the nearest one to the tap location
                        val nearestIndex = points.indices.minByOrNull { hypot(points[it].x - x, points[it].y - y) } ?: -1
                        if (nearestIndex != -1) {
                            points[nearestIndex].set(x, y)
                            draggedPointIndex = nearestIndex
                            invalidate()
                        }
                    }
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedPointIndex != -1) {
                    points[draggedPointIndex].set(x, y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                draggedPointIndex = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun clearPoints() {
        points.clear()
        onPointsChanged?.invoke(points.size)
        invalidate()
    }

    fun initializePoints(newPoints: List<PointF>) {
        points.clear()
        points.addAll(newPoints)
        onPointsChanged?.invoke(points.size)
        invalidate()
    }
}
