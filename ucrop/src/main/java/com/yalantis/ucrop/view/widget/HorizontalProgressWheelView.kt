package com.yalantis.ucrop.view.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.yalantis.ucrop.R

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 */
class HorizontalProgressWheelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    private val mCanvasClipBounds = Rect()

    private var mScrollingListener: ScrollingListener? = null
    private var mLastTouchedPosition = 0f

    private val mProgressLinePaint: Paint
    private val mProgressMiddleLinePaint: Paint
    private val mProgressLineWidth: Int
    private val mProgressLineHeight: Int
    private val mProgressLineMargin: Int

    private var mScrollStarted = false
    private var mTotalScrollDistance = 0f

    private var mMiddleLineColor: Int

    init {
        mMiddleLineColor = ContextCompat.getColor(context, R.color.ucrop_color_widget_rotate_mid_line)
        mProgressLineWidth = context.resources.getDimensionPixelSize(R.dimen.ucrop_width_horizontal_wheel_progress_line)
        mProgressLineHeight = context.resources.getDimensionPixelSize(R.dimen.ucrop_height_horizontal_wheel_progress_line)
        mProgressLineMargin = context.resources.getDimensionPixelSize(R.dimen.ucrop_margin_horizontal_wheel_progress_line)
        mProgressLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mProgressLinePaint.style = Paint.Style.STROKE
        mProgressLinePaint.strokeWidth = mProgressLineWidth.toFloat()
        mProgressLinePaint.color = ResourcesCompat.getColor(resources, R.color.ucrop_color_progress_wheel_line, null)
        mProgressMiddleLinePaint = Paint(mProgressLinePaint)
        mProgressMiddleLinePaint.color = mMiddleLineColor
        mProgressMiddleLinePaint.strokeCap = Paint.Cap.ROUND
        mProgressMiddleLinePaint.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.ucrop_width_middle_wheel_progress_line).toFloat()
    }

    fun setScrollingListener(scrollingListener: ScrollingListener?) {
        mScrollingListener = scrollingListener
    }

    fun setMiddleLineColor(@ColorInt middleLineColor: Int) {
        mMiddleLineColor = middleLineColor
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> mLastTouchedPosition = event.x
            MotionEvent.ACTION_UP -> if (mScrollingListener != null) {
                mScrollStarted = false
                mScrollingListener?.onScrollEnd()
            }
            MotionEvent.ACTION_MOVE -> {
                val distance = event.x - mLastTouchedPosition
                if (distance != 0f) {
                    if (!mScrollStarted) {
                        mScrollStarted = true
                        if (mScrollingListener != null) {
                            mScrollingListener!!.onScrollStart()
                        }
                    }
                    onScrollEvent(event, distance)
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.getClipBounds(mCanvasClipBounds)

        val linesCount = mCanvasClipBounds.width() / (mProgressLineWidth + mProgressLineMargin)
        val deltaX = mTotalScrollDistance % (mProgressLineMargin + mProgressLineWidth).toFloat()

        for (i in 0 until linesCount) {
            mProgressLinePaint.alpha = when {
                i < linesCount / 4 -> (255 * (i / (linesCount / 4).toFloat())).toInt()
                i > linesCount * 3 / 4 -> (255 * ((linesCount - i) / (linesCount / 4).toFloat())).toInt()
                else -> 255
            }
            canvas.drawLine(
                    -deltaX + mCanvasClipBounds.left + i * (mProgressLineWidth + mProgressLineMargin),
                    mCanvasClipBounds.centerY() - mProgressLineHeight / 4.0f,
                    -deltaX + mCanvasClipBounds.left + i * (mProgressLineWidth + mProgressLineMargin),
                    mCanvasClipBounds.centerY() + mProgressLineHeight / 4.0f, mProgressLinePaint)
        }
        canvas.drawLine(mCanvasClipBounds.centerX().toFloat(), mCanvasClipBounds.centerY() - mProgressLineHeight / 2.0f, mCanvasClipBounds.centerX().toFloat(), mCanvasClipBounds.centerY() + mProgressLineHeight / 2.0f, mProgressMiddleLinePaint)
    }

    private fun onScrollEvent(event: MotionEvent, distance: Float) {
        mTotalScrollDistance -= distance
        postInvalidate()
        mLastTouchedPosition = event.x
        mScrollingListener?.onScroll(-distance, mTotalScrollDistance)
    }

    interface ScrollingListener {
        fun onScrollStart()
        fun onScroll(delta: Float, totalDistance: Float)
        fun onScrollEnd()
    }

}