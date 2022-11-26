package com.sjqnice.mlkit.mlkit.textdetector

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import com.google.mlkit.vision.text.Text
import com.sjqnice.mlkit.mlkit.GraphicOverlay
import com.sjqnice.mlkit.mlkit.GraphicOverlay.Graphic
import kotlin.math.max
import kotlin.math.min

class FitSizeTextGraphic(overlay: GraphicOverlay?, private val text: Text) : Graphic(overlay) {
    private val rectPaint: Paint = Paint()
    private val textPaint: Paint
    private val bounds:Rect
    private var stillImageHeight = 0
    private var rectMap:MutableMap<RectF,String> = mutableMapOf()
    init {
        rectPaint.color = MARKER_COLOR
        rectPaint.style = Paint.Style.FILL
        rectPaint.strokeWidth = STROKE_WIDTH
        textPaint = Paint()
        textPaint.color = TEXT_COLOR
//        textPaint.textSize = TEXT_SIZE
        bounds = Rect()
        // Redraw the overlay, as this graphic has been added.
        postInvalidate()
    }

    override fun draw(canvas: Canvas) {
        for (textBlock in text.textBlocks) {
            for (line in textBlock.lines){
                val rect = RectF(line.boundingBox)
                drawText(line.text, rect, canvas)
            }
        }
    }

    private fun drawText(text: String, rect: RectF,canvas: Canvas) {
        val translateY = if (stillImageHeight == 0) 0f else canvas.height - stillImageHeight - (canvas.height - stillImageHeight) / 2f
        val x0 = translateX(rect.left)
        val x1 = translateX(rect.right)
        rect.left = min(x0, x1)
        rect.right = max(x0, x1)
        rect.top = translateY(rect.top) + translateY
        rect.bottom = translateY(rect.bottom) + translateY
        canvas.drawRect(rect, rectPaint)
        rectMap[rect] = text

        setTextSizeForWidth(text,rect.width())
        canvas.drawText(text, rect.left, rect.bottom - STROKE_WIDTH, textPaint)
    }

    private fun setTextSizeForWidth(text: String, desiredWidth: Float){
        textPaint.textSize = TEXT_SIZE
        textPaint.getTextBounds(text,0,text.length,bounds)
        val desiredTextSize = TEXT_SIZE * desiredWidth / bounds.width()
        textPaint.textSize = desiredTextSize
    }

    fun setStillImageHeight(height: Int){
        stillImageHeight = height
        postInvalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN){
            val x = event.x
            val y = event.y
            for (rect in rectMap.keys){
                if (rect.contains(x,y)){
                    onClickListener?.let {
                        it(rectMap[rect]!!)
                    }
                }
            }
        }
        return true
    }

    private var onClickListener: ((String) -> Unit)? = null

    fun setOnClickListener(listener:(String) -> Unit){
        onClickListener = listener
    }

    companion object {
        private const val TEXT_COLOR = Color.BLACK
        private const val MARKER_COLOR = Color.WHITE
        private const val TEXT_SIZE = 30.0f
        private const val STROKE_WIDTH = 4.0f
    }
}