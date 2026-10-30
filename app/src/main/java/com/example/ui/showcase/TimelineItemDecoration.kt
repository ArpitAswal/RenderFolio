package com.example.ui.showcase

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.R

class TimelineItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private val lineX = 16f * density
    private val lineWidth = 1.5f * density
    private val dotRadius = 4f * density // 8dp diameter

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent_blue)
        strokeWidth = lineWidth
        style = Paint.Style.STROKE
        alpha = 180
    }

    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent_blue)
        style = Paint.Style.FILL
        alpha = 60
    }

    private val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent_blue)
        style = Paint.Style.FILL
    }

    private val dotCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, android.R.color.white)
        style = Paint.Style.FILL
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount
        if (childCount == 0) return

        val firstChild = parent.getChildAt(0)
        val lastChild = parent.getChildAt(childCount - 1)

        val firstCenterY = firstChild.top + (22f * density)
        val lastCenterY = lastChild.top + (22f * density)

        // Draw continuous vertical accent line
        c.drawLine(lineX, firstCenterY, lineX, lastCenterY, linePaint)

        // Draw illuminated circular node dots beside each position entry
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val centerY = child.top + (22f * density)

            // Outer glow ring
            c.drawCircle(lineX, centerY, dotRadius + (2.5f * density), dotGlowPaint)
            // Solid accent node
            c.drawCircle(lineX, centerY, dotRadius, dotFillPaint)
            // Core highlight
            c.drawCircle(lineX, centerY, dotRadius * 0.45f, dotCenterPaint)
        }
    }
}
