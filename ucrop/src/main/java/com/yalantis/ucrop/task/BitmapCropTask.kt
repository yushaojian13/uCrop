package com.yalantis.ucrop.task

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.AsyncTask
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.model.CropParameters
import com.yalantis.ucrop.model.ImageState
import com.yalantis.ucrop.util.BitmapLoadUtils.close
import com.yalantis.ucrop.util.FileUtils.copyFile
import com.yalantis.ucrop.util.ImageHeaderParser.Companion.copyExif
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Crops part of image that fills the crop bounds.
 *
 *
 * First image is downscaled if max size was set and if resulting image is larger that max size.
 * Then image is rotated accordingly.
 * Finally new Bitmap object is created and saved to file.
 */
class BitmapCropTask(context: Context, viewBitmap: Bitmap?, imageState: ImageState, cropParameters: CropParameters,
                     cropCallback: BitmapCropCallback?) : AsyncTask<Void, Void, Throwable>() {

    private val mContext: WeakReference<Context> = WeakReference(context)
    private var mViewBitmap: Bitmap? = viewBitmap
    private val mCropRect: RectF = imageState.cropRect
    private val mCurrentImageRect: RectF = imageState.currentImageRect
    private var mCurrentScale: Float = imageState.currentScale
    private val mCurrentAngle: Float = imageState.currentAngle
    private val mMaxResultImageSizeX: Int = cropParameters.maxResultImageSizeX
    private val mMaxResultImageSizeY: Int = cropParameters.maxResultImageSizeY
    private val mCompressFormat: CompressFormat = cropParameters.compressFormat
    private val mCompressQuality: Int = cropParameters.compressQuality
    private val mImageInputPath: String = cropParameters.imageInputPath
    private val mImageOutputPath: String = cropParameters.imageOutputPath
    private val mCropCallback: BitmapCropCallback? = cropCallback

    private var mCroppedImageWidth = 0
    private var mCroppedImageHeight = 0
    private var cropOffsetX = 0
    private var cropOffsetY = 0

    override fun doInBackground(vararg params: Void): Throwable? {
        val viewBitmap = mViewBitmap
        when {
            viewBitmap == null -> {
                return NullPointerException("ViewBitmap is null")
            }
            viewBitmap.isRecycled -> {
                return NullPointerException("ViewBitmap is recycled")
            }
            mCurrentImageRect.isEmpty -> {
                return NullPointerException("CurrentImageRect is empty")
            }
            else -> {
                try {
                    crop()
                } catch (throwable: Throwable) {
                    return throwable
                } finally {
                    mViewBitmap = null
                }
                return null
            }
        }
    }

    @Throws(IOException::class)
    private fun crop(): Boolean {
        var viewBitmap = mViewBitmap ?: return false

        // Downsize if needed
        if (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0) {
            val cropWidth = mCropRect.width() / mCurrentScale
            val cropHeight = mCropRect.height() / mCurrentScale

            if (cropWidth > mMaxResultImageSizeX || cropHeight > mMaxResultImageSizeY) {
                val scaleX = mMaxResultImageSizeX / cropWidth
                val scaleY = mMaxResultImageSizeY / cropHeight
                val resizeScale = min(scaleX, scaleY)

                val resizedBitmap = Bitmap.createScaledBitmap(viewBitmap,
                        (viewBitmap.width * resizeScale).roundToInt(),
                        (viewBitmap.height * resizeScale).roundToInt(), false)
                if (viewBitmap != resizedBitmap) {
                    viewBitmap.recycle()
                }
                viewBitmap = resizedBitmap
                mCurrentScale /= resizeScale
            }
        }

        // Rotate if needed
        if (mCurrentAngle != 0f) {
            val tempMatrix = Matrix()
            tempMatrix.setRotate(mCurrentAngle, viewBitmap.width / 2.toFloat(), viewBitmap.height / 2.toFloat())

            val rotatedBitmap = Bitmap.createBitmap(viewBitmap, 0, 0, viewBitmap.width, viewBitmap.height,
                    tempMatrix, true)
            if (viewBitmap != rotatedBitmap) {
                viewBitmap.recycle()
            }
            viewBitmap = rotatedBitmap
        }

        cropOffsetX = ((mCropRect.left - mCurrentImageRect.left) / mCurrentScale).roundToInt()
        cropOffsetY = ((mCropRect.top - mCurrentImageRect.top) / mCurrentScale).roundToInt()
        mCroppedImageWidth = (mCropRect.width() / mCurrentScale).roundToInt()
        mCroppedImageHeight = (mCropRect.height() / mCurrentScale).roundToInt()

        val shouldCrop = shouldCrop(mCroppedImageWidth, mCroppedImageHeight)
        Log.i(TAG, "Should crop: $shouldCrop")

        return if (shouldCrop) {
            val originalExif = ExifInterface(mImageInputPath)
            saveImage(Bitmap.createBitmap(viewBitmap, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight))
            if (mCompressFormat == CompressFormat.JPEG) {
                copyExif(originalExif, mCroppedImageWidth, mCroppedImageHeight, mImageOutputPath)
            }
            true
        } else {
            copyFile(mImageInputPath, mImageOutputPath)
            false
        }
    }

    @Throws(FileNotFoundException::class)
    private fun saveImage(croppedBitmap: Bitmap) {
        val context = mContext.get() ?: return
        var outputStream: OutputStream? = null
        try {
            outputStream = context.contentResolver.openOutputStream(Uri.fromFile(File(mImageOutputPath)))
            croppedBitmap.compress(mCompressFormat, mCompressQuality, outputStream)
            croppedBitmap.recycle()
        } finally {
            close(outputStream)
        }
    }

    /**
     * Check whether an image should be cropped at all or just file can be copied to the destination path.
     * For each 1000 pixels there is one pixel of error due to matrix calculations etc.
     *
     * @param width  - crop area width
     * @param height - crop area height
     * @return - true if image must be cropped, false - if original image fits requirements
     */
    private fun shouldCrop(width: Int, height: Int): Boolean {
        var pixelError = 1
        pixelError += (max(width, height) / 1000f).roundToInt()
        return (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0
                || abs(mCropRect.left - mCurrentImageRect.left) > pixelError
                || abs(mCropRect.top - mCurrentImageRect.top) > pixelError
                || abs(mCropRect.bottom - mCurrentImageRect.bottom) > pixelError
                || abs(mCropRect.right - mCurrentImageRect.right) > pixelError
                || mCurrentAngle != 0f)
    }

    override fun onPostExecute(t: Throwable?) {
        if (mCropCallback != null) {
            if (t == null) {
                val uri = Uri.fromFile(File(mImageOutputPath))
                mCropCallback.onBitmapCropped(uri, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight)
            } else {
                mCropCallback.onCropFailure(t)
            }
        }
    }

    companion object {
        private const val TAG = "BitmapCropTask"
    }

}