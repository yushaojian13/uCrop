package com.yalantis.ucrop.view.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.yalantis.ucrop.R
import com.yalantis.ucrop.model.AspectRatio
import com.yalantis.ucrop.view.CropImageView
import java.util.*

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 */
@SuppressLint("CustomViewStyleable")
class AspectRatioTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val mCanvasClipBounds = Rect()
    private val mDotPaint: Paint
    private val mDotSize: Int
    private var mAspectRatio: Float
    private var mAspectRatioTitle: String?
    private var mAspectRatioX: Float
    private var mAspectRatioY: Float

    init {
        gravity = Gravity.CENTER_HORIZONTAL

        val a = context.obtainStyledAttributes(attrs, R.styleable.ucrop_AspectRatioTextView)
        mAspectRatioTitle = a.getString(R.styleable.ucrop_AspectRatioTextView_ucrop_artv_ratio_title)
        mAspectRatioX = a.getFloat(R.styleable.ucrop_AspectRatioTextView_ucrop_artv_ratio_x, CropImageView.SOURCE_IMAGE_ASPECT_RATIO)
        mAspectRatioY = a.getFloat(R.styleable.ucrop_AspectRatioTextView_ucrop_artv_ratio_y, CropImageView.SOURCE_IMAGE_ASPECT_RATIO)

        mAspectRatio = if (mAspectRatioX == CropImageView.SOURCE_IMAGE_ASPECT_RATIO || mAspectRatioY == CropImageView.SOURCE_IMAGE_ASPECT_RATIO) {
            CropImageView.SOURCE_IMAGE_ASPECT_RATIO
        } else {
            mAspectRatioX / mAspectRatioY
        }

        mDotSize = context.resources.getDimensionPixelSize(R.dimen.ucrop_size_dot_scale_text_view)
        mDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mDotPaint.style = Paint.Style.FILL

        setTitle()

        val activeColor = ResourcesCompat.getColor(resources, R.color.ucrop_color_widget_active, null)
        applyActiveColor(activeColor)

        a.recycle()
    }

    /**
     * @param activeColor the resolved color for active elements
     */
    fun setActiveColor(@ColorInt activeColor: Int) {
        applyActiveColor(activeColor)
        invalidate()
    }

    fun setAspectRatio(aspectRatio: AspectRatio) {
        mAspectRatioTitle = aspectRatio.aspectRatioTitle
        mAspectRatioX = aspectRatio.aspectRatioX
        mAspectRatioY = aspectRatio.aspectRatioY

        mAspectRatio = if (mAspectRatioX == CropImageView.SOURCE_IMAGE_ASPECT_RATIO || mAspectRatioY == CropImageView.SOURCE_IMAGE_ASPECT_RATIO) {
            CropImageView.SOURCE_IMAGE_ASPECT_RATIO
        } else {
            mAspectRatioX / mAspectRatioY
        }

        setTitle()
    }

    fun getAspectRatio(toggleRatio: Boolean): Float {
        if (toggleRatio) {
            toggleAspectRatio()
            setTitle()
        }
        return mAspectRatio
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isSelected) {
            canvas.getClipBounds(mCanvasClipBounds)

            val x = (mCanvasClipBounds.right - mCanvasClipBounds.left) / 2.0f
            val y = mCanvasClipBounds.bottom - mCanvasClipBounds.top / 2f - mDotSize * MARGIN_MULTIPLIER

            canvas.drawCircle(x, y, mDotSize / 2f, mDotPaint)
        }
    }

    private fun applyActiveColor(@ColorInt activeColor: Int) {
        mDotPaint.color = activeColor
        val textViewColorStateList = ColorStateList(arrayOf(
                intArrayOf(android.R.attr.state_selected), intArrayOf(0)),
                intArrayOf(activeColor, ContextCompat.getColor(context, R.color.ucrop_color_widget)
                ))

        setTextColor(textViewColorStateList)
    }

    private fun toggleAspectRatio() {
        if (mAspectRatio != CropImageView.SOURCE_IMAGE_ASPECT_RATIO) {
            val tempRatioW = mAspectRatioX
            mAspectRatioX = mAspectRatioY
            mAspectRatioY = tempRatioW
            mAspectRatio = mAspectRatioX / mAspectRatioY
        }
    }

    private fun setTitle() {
        text = if (!TextUtils.isEmpty(mAspectRatioTitle)) {
            mAspectRatioTitle
        } else {
            String.format(Locale.US, "%d:%d", mAspectRatioX.toInt(), mAspectRatioY.toInt())
        }
    }

    companion object {
        private const val MARGIN_MULTIPLIER = 1.5f
    }
}