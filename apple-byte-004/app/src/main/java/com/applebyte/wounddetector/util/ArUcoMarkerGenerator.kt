package com.applebyte.wounddetector.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.min

object ArUcoMarkerGenerator {
    
    private val markerDictionary = mapOf(
        0 to listOf(0, 0, 1, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 1, 0, 1),
        1 to listOf(1, 0, 0, 1, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0, 1, 0),
        2 to listOf(1, 0, 1, 0, 1, 1, 0, 0, 0, 0, 1, 1, 0, 1, 0, 1),
        3 to listOf(1, 1, 0, 0, 0, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 0),
        4 to listOf(0, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 1),
        5 to listOf(0, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 1, 0, 0, 1),
        6 to listOf(0, 0, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 1, 0),
        7 to listOf(1, 0, 0, 1, 1, 1, 1, 0, 0, 0, 1, 0, 1, 1, 0, 0),
        8 to listOf(1, 1, 1, 0, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1),
        9 to listOf(0, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 1, 0)
    )
    
    fun generateMarkerBitmap(markerId: Int, sizePx: Int = 400): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val paint = Paint().apply {
            isAntiAlias = false
        }
        
        val borderSize = sizePx / 10
        val markerSize = sizePx - 2 * borderSize
        val cellSize = markerSize / 6
        
        canvas.drawColor(Color.WHITE)
        
        paint.color = Color.BLACK
        canvas.drawRect(0f, 0f, borderSize.toFloat(), sizePx.toFloat(), paint)
        canvas.drawRect(sizePx - borderSize.toFloat(), 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
        canvas.drawRect(0f, 0f, sizePx.toFloat(), borderSize.toFloat(), paint)
        canvas.drawRect(0f, sizePx - borderSize.toFloat(), sizePx.toFloat(), sizePx.toFloat(), paint)
        
        val pattern = markerDictionary[markerId] ?: markerDictionary[0]!!
        
        val startX = borderSize
        val startY = borderSize
        
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val index = row * 4 + col
                if (index < pattern.size) {
                    paint.color = if (pattern[index] == 1) Color.BLACK else Color.WHITE
                } else {
                    paint.color = Color.BLACK
                }
                
                val x = startX + col * cellSize
                val y = startY + row * cellSize
                canvas.drawRect(
                    x.toFloat(), y.toFloat(),
                    (x + cellSize).toFloat(), (y + cellSize).toFloat(),
                    paint
                )
            }
        }
        
        paint.color = Color.WHITE
        canvas.drawRect(
            startX.toFloat(), startY.toFloat(),
            (startX + 4 * cellSize).toFloat(), (startY + 4 * cellSize).toFloat(),
            paint
        )
        
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val index = row * 4 + col
                if (index < pattern.size) {
                    paint.color = if (pattern[index] == 1) Color.BLACK else Color.WHITE
                } else {
                    paint.color = Color.BLACK
                }
                
                val x = startX + col * cellSize
                val y = startY + row * cellSize
                canvas.drawRect(
                    x.toFloat(), y.toFloat(),
                    (x + cellSize).toFloat(), (y + cellSize).toFloat(),
                    paint
                )
            }
        }
        
        paint.color = Color.parseColor("#1976D2")
        paint.textSize = sizePx / 10f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("ID: $markerId", sizePx / 2f, sizePx / 2f + sizePx / 20f, paint)
        
        return bitmap
    }
    
    fun getMarkerIds(): List<Int> = markerDictionary.keys.toList()
}
