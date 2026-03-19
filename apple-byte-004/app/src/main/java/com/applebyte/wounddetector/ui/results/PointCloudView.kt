package com.applebyte.wounddetector.ui.results

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class PointCloudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val points3d = mutableListOf<Triple<Float, Float, Float>>()
    private val projectedPoints = mutableListOf<Pair<Float, Float>>()
    
    private var rotationX = 0f
    private var rotationY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    
    private val pointPaint = Paint().apply {
        color = Color.parseColor("#1976D2")
        strokeWidth = 3f
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val woundPaint = Paint().apply {
        color = Color.parseColor("#E57373")
        strokeWidth = 5f
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val linePaint = Paint().apply {
        color = Color.parseColor("#90EE90")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
    }
    
    private val axisPaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val woundPoints = mutableListOf<Triple<Float, Float, Float>>()
    
    var showAxes = true
    var showWoundArea = false

    fun setPoints3D(points: List<Triple<Float, Float, Float>>) {
        points3d.clear()
        points3d.addAll(points)
        calculateProjections()
        invalidate()
    }
    
    fun setWoundPoints(points: List<Triple<Float, Float, Float>>) {
        woundPoints.clear()
        woundPoints.addAll(points)
        calculateProjections()
        invalidate()
    }
    
    fun clearPoints() {
        points3d.clear()
        woundPoints.clear()
        projectedPoints.clear()
        invalidate()
    }

    private fun calculateProjections() {
        projectedPoints.clear()
        
        for ((x, y, z) in points3d) {
            val rotated = rotatePoint(x, y, z)
            val projected = projectTo2D(rotated.first, rotated.second, rotated.third)
            projectedPoints.add(projected)
        }
    }
    
    private fun rotatePoint(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        // Rotate around Y axis
        val radY = Math.toRadians(rotationY.toDouble())
        val x1 = (x * cos(radY) - z * sin(radY)).toFloat()
        val z1 = (x * sin(radY) + z * cos(radY)).toFloat()
        
        // Rotate around X axis
        val radX = Math.toRadians(rotationX.toDouble())
        val y1 = (y * cos(radX) - z1 * sin(radX)).toFloat()
        val z2 = (y * sin(radX) + z1 * cos(radX)).toFloat()
        
        return Triple(x1, y1, z2)
    }
    
    private fun projectTo2D(x: Float, y: Float, z: Float): Pair<Float, Float> {
        val fov = 500f
        val perspective = fov / (fov + z)
        
        val projX = x * perspective * scale + offsetX
        val projY = y * perspective * scale + offsetY
        
        return Pair(projX, projY)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        offsetX = w / 2f
        offsetY = h / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        canvas.drawColor(Color.parseColor("#1A1A2E"))
        
        if (showAxes) {
            drawAxes(canvas)
        }
        
        // Draw regular points
        for ((index, projected) in projectedPoints.withIndex()) {
            val (x, y) = projected
            val z = points3d.getOrNull(index)?.third ?: 0f
            
            // Color based on depth
            val alpha = ((1 - (z / 1000f + 0.5f).coerceIn(0f, 1f)) * 200).toInt().coerceIn(50, 255)
            pointPaint.alpha = alpha
            
            canvas.drawCircle(x, y, 3f * scale, pointPaint)
        }
        
        // Draw wound points with different color
        for ((x, y, z) in woundPoints) {
            val rotated = rotatePoint(x, y, z)
            val projected = projectTo2D(rotated.first, rotated.second, rotated.third)
            
            val alpha = ((1 - (z / 1000f + 0.5f).coerceIn(0f, 1f)) * 255).toInt().coerceIn(100, 255)
            woundPaint.alpha = alpha
            
            canvas.drawCircle(projected.first, projected.second, 5f * scale, woundPaint)
        }
        
        // Draw info
        canvas.drawText("Drag to rotate | Scale: ${String.format("%.1f", scale)}x", 20f, height - 20f, textPaint)
    }

    private fun drawAxes(canvas: Canvas) {
        val centerX = offsetX
        val centerY = offsetY
        val axisLength = 80f * scale
        
        // X axis (red)
        axisPaint.color = Color.RED
        canvas.drawLine(centerX, centerY, centerX + axisLength, centerY, axisPaint)
        
        // Y axis (green)
        axisPaint.color = Color.GREEN
        canvas.drawLine(centerX, centerY, centerX, centerY - axisLength, axisPaint)
        
        // Z axis (blue)
        axisPaint.color = Color.BLUE
        canvas.drawLine(centerX, centerY, centerX + axisLength * 0.7f, centerY + axisLength * 0.7f, axisPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                
                rotationY += dx * 0.5f
                rotationX += dy * 0.5f
                
                lastTouchX = event.x
                lastTouchY = event.y
                
                calculateProjections()
                invalidate()
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                val scaleFactor = 1 + event.y / 1000f
                scale = (scale * scaleFactor).coerceIn(0.1f, 5f)
                calculateProjections()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    fun resetView() {
        rotationX = 0f
        rotationY = 0f
        scale = 1f
        calculateProjections()
        invalidate()
    }
}
