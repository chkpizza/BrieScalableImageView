package com.wantique.scalable

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class BrieScalableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private var minScale: Float = 1f
    private var maxScale: Float = 4f
    private var savedScale: Float = 1f
    private var isEnabledDoubleTap: Boolean = true
    private var isDoubleTap: Boolean = false
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0
    private var drawableOriginWidth: Float = 0f
    private var drawableOriginHeight: Float = 0f
    private var isPanning = false

    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    private val matrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val startMatrixValues = FloatArray(9)
    private val point = PointF()

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.BrieScalableImageView)
        minScale = typedArray.getFloat(R.styleable.BrieScalableImageView_minimum_scale, 1f)
        maxScale = typedArray.getFloat(R.styleable.BrieScalableImageView_maximum_scale, 4f)
        isEnabledDoubleTap = typedArray.getBoolean(R.styleable.BrieScalableImageView_enabled_double_tap, true)
        typedArray.recycle()

        scaleGestureDetector = ScaleGestureDetector(context, ScaleGestureListener())
        gestureDetector = GestureDetector(context, GestureListener())

        imageMatrix = matrix
        scaleType = ScaleType.MATRIX
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        viewHeight = MeasureSpec.getSize(heightMeasureSpec)

        initViewPosition()
    }

    private fun initViewPosition() {
        if(drawable == null || drawable.intrinsicHeight == 0 || drawable.intrinsicWidth == 0) {
            return
        }

        savedScale = 1f

        val scaleX = viewWidth / drawable.intrinsicWidth.toFloat()
        val scaleY = viewHeight / drawable.intrinsicHeight.toFloat()
        val minScale = Math.min(scaleX, scaleY)
        matrix.setScale(minScale, minScale)

        val translateX = ((viewWidth) - minScale * drawable.intrinsicWidth.toFloat()) / 2
        val translateY = ((viewHeight) - minScale * drawable.intrinsicHeight.toFloat()) / 2
        matrix.postTranslate(translateX, translateY)

        drawableOriginWidth = viewWidth - 2 * translateX
        drawableOriginHeight = viewHeight - 2 * translateY

        Matrix(matrix).getValues(startMatrixValues)
        imageMatrix = matrix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if(isDoubleTap) {
            processDoubleTap(event)
        } else {
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    point.set(scaleGestureDetector.focusX, scaleGestureDetector.focusY)
                    isPanning = true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    isPanning = false
                }

                MotionEvent.ACTION_MOVE -> {
                    if(isPanning) {
                        val translateX = getPanningSize(scaleGestureDetector.focusX - point.x, viewWidth.toFloat(), drawableOriginWidth * savedScale)
                        val translateY = getPanningSize(scaleGestureDetector.focusY - point.y, viewHeight.toFloat(), drawableOriginHeight * savedScale)

                        matrix.postTranslate(translateX, translateY)
                        preventTranslateOutOfDrawable()
                        point.set(scaleGestureDetector.focusX, scaleGestureDetector.focusY)
                    }
                }
            }
            imageMatrix = matrix
        }

        return true
    }

    private fun processDoubleTap(event: MotionEvent) {
        savedScale = if(savedScale <= minScale) {
            matrix.postScale(maxScale, maxScale, event.x, event.y)
            maxScale
        } else {
            matrix.setValues(startMatrixValues)
            minScale
        }

        animateMatrix()

        isDoubleTap = false
    }

    private fun animateMatrix() {
        val targetValues = FloatArray(9)
        matrix.getValues(targetValues)

        val beginMatrix = Matrix(imageMatrix)
        val matrixValues = FloatArray(9)
        beginMatrix.getValues(matrixValues)

        val scaleXDelta = targetValues[Matrix.MSCALE_X] - matrixValues[Matrix.MSCALE_X]
        val scaleYDelta = targetValues[Matrix.MSCALE_Y] - matrixValues[Matrix.MSCALE_Y]
        val translateXDelta = targetValues[Matrix.MTRANS_X] - matrixValues[Matrix.MTRANS_X]
        val translateYDelta = targetValues[Matrix.MTRANS_Y] - matrixValues[Matrix.MTRANS_Y]

        val resetAnimator = ValueAnimator.ofFloat(0f, 1f)
        resetAnimator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            val activeMatrix = Matrix(imageMatrix)
            val values = FloatArray(9)

            override fun onAnimationUpdate(animation: ValueAnimator) {
                val value = animation.animatedValue as Float
                activeMatrix.set(beginMatrix)
                activeMatrix.getValues(values)

                values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X] + translateXDelta * value
                values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y] + translateYDelta * value
                values[Matrix.MSCALE_X] = values[Matrix.MSCALE_X] + scaleXDelta * value
                values[Matrix.MSCALE_Y] = values[Matrix.MSCALE_Y] + scaleYDelta * value
                activeMatrix.setValues(values)
                imageMatrix = activeMatrix
            }
        })

        resetAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
            }

            override fun onAnimationEnd(animation: Animator) {
                imageMatrix = matrix
            }

            override fun onAnimationCancel(animation: Animator) {
            }

            override fun onAnimationRepeat(animation: Animator) {
            }
        })

        resetAnimator.setDuration(200)
        resetAnimator.start()
    }

    private fun alignToCenter() {
        if(savedScale == 1f) {
            matrix.setValues(startMatrixValues)
        }
    }

    private fun preventTranslateOutOfDrawable() {
        matrix.getValues(matrixValues)

        val translateX = getEscapeTranslateSize(matrixValues[Matrix.MTRANS_X], viewWidth.toFloat(), drawableOriginWidth * savedScale)
        val translateY = getEscapeTranslateSize(matrixValues[Matrix.MTRANS_Y], viewHeight.toFloat(), drawableOriginHeight * savedScale)

        if(translateX != 0f || translateY != 0f) {
            matrix.postTranslate(translateX, translateY)
        }
    }

    private fun getEscapeTranslateSize(translate: Float, viewDimension: Float, scaledDrawableDimension: Float): Float {
        return if(scaledDrawableDimension > viewDimension) {
            if(translate < viewDimension - scaledDrawableDimension) {
                -translate + (viewDimension - scaledDrawableDimension)
            } else if(translate > 0f) {
                -translate
            } else {
                0f
            }
        } else {
            0f
        }
    }

    private fun getPanningSize(delta: Float, viewDimension: Float, scaledDrawableDimension: Float): Float {
        return if(scaledDrawableDimension <= viewDimension) {
            0f
        } else {
            delta
        }
    }

    inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            return true
        }

        override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
            val scaleFactor: Float
            if(savedScale * scaleGestureDetector.scaleFactor > maxScale) {
                scaleFactor = maxScale / savedScale
                savedScale = maxScale
            } else if(savedScale * scaleGestureDetector.scaleFactor < minScale) {
                scaleFactor = minScale / savedScale
                savedScale = minScale
            } else {
                scaleFactor = scaleGestureDetector.scaleFactor
                savedScale *= scaleGestureDetector.scaleFactor
            }

            matrix.postScale(scaleFactor, scaleFactor, scaleGestureDetector.focusX, scaleGestureDetector.focusY)
            preventTranslateOutOfDrawable()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            alignToCenter()
        }
    }

    inner class GestureListener : GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {
        override fun onDown(motionEvent: MotionEvent): Boolean {
            return false
        }

        override fun onShowPress(motionEvent: MotionEvent) {}

        override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
            return false
        }

        override fun onScroll(e1: MotionEvent?, motionEvent: MotionEvent, v: Float, v1: Float): Boolean {
            return false
        }

        override fun onLongPress(motionEvent: MotionEvent) {}

        override fun onFling(e1: MotionEvent?, motionEvent: MotionEvent, v: Float, v1: Float): Boolean {
            return false
        }

        override fun onSingleTapConfirmed(motionEvent: MotionEvent): Boolean {
            return false
        }

        override fun onDoubleTap(motionEvent: MotionEvent): Boolean {
            isDoubleTap = true
            return false
        }

        override fun onDoubleTapEvent(motionEvent: MotionEvent): Boolean {
            return false
        }
    }
}