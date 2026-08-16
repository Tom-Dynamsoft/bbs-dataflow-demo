package com.dynamsoft.bbsdatareceiver.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.dynamsoft.dbr.BarcodeResultItem

/**
 * Draws quadrilateral highlights around each barcode on a camera frame bitmap.
 */
object BarcodeImageAnnotator {

    private val strokePaint = Paint().apply {
        color = Color.argb(220, 0, 255, 0)
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.argb(40, 0, 255, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }

    /**
     * Draws quadrilateral highlights on [source] for each barcode in [items].
     * [scaleX] and [scaleY] map barcode coordinates (from the video frame) to the target bitmap.
     * Returns a new mutable bitmap with annotations.
     */
    fun annotate(
        source: Bitmap,
        items: Array<BarcodeResultItem>,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        offsetX: Int = 0,
        offsetY: Int = 0
    ): Bitmap {
        val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)

        // Scale text size proportionally
        val scaledTextPaint = if (scaleX != 1f || scaleY != 1f) {
            Paint(textPaint).apply {
                textSize = textPaint.textSize * maxOf(scaleX, scaleY)
            }
        } else textPaint

        val scaledStrokePaint = if (scaleX != 1f || scaleY != 1f) {
            Paint(strokePaint).apply {
                strokeWidth = strokePaint.strokeWidth * maxOf(scaleX, scaleY)
            }
        } else strokePaint

        for (item in items) {
            val loc = item.location ?: continue
            val points = loc.points ?: continue
            if (points.size < 4) continue

            val path = Path().apply {
                moveTo((points[0].x - offsetX) * scaleX, (points[0].y - offsetY) * scaleY)
                lineTo((points[1].x - offsetX) * scaleX, (points[1].y - offsetY) * scaleY)
                lineTo((points[2].x - offsetX) * scaleX, (points[2].y - offsetY) * scaleY)
                lineTo((points[3].x - offsetX) * scaleX, (points[3].y - offsetY) * scaleY)
                close()
            }
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, scaledStrokePaint)

            val label = item.text?.take(30) ?: ""
            if (label.isNotEmpty()) {
                canvas.drawText(
                    label,
                    (points[0].x - offsetX) * scaleX,
                    (points[0].y - offsetY) * scaleY - 10f * scaleY,
                    scaledTextPaint
                )
            }
        }

        return annotated
    }
}
