package com.yalantis.ucrop.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.AsyncTask
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.exifinterface.media.ExifInterface
import com.yalantis.ucrop.callback.BitmapLoadCallback
import com.yalantis.ucrop.task.BitmapLoadTask
import java.io.Closeable
import java.io.IOException

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 */
object BitmapLoadUtils {

    private const val TAG = "BitmapLoadUtils"

    @JvmStatic
    fun decodeBitmapInBackground(context: Context,
                                 uri: Uri, outputUri: Uri?,
                                 requiredWidth: Int, requiredHeight: Int,
                                 loadCallback: BitmapLoadCallback) {
        BitmapLoadTask(context, uri, outputUri, requiredWidth, requiredHeight, loadCallback)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    @JvmStatic
    fun transformBitmap(bitmap: Bitmap, transformMatrix: Matrix): Bitmap {
        var result = bitmap
        try {
            val converted = Bitmap.createBitmap(result, 0, 0, result.width, result.height, transformMatrix, true)
            if (!result.sameAs(converted)) {
                result = converted
            }
        } catch (error: OutOfMemoryError) {
            Log.e(TAG, "transformBitmap: ", error)
        }
        return result
    }

    @JvmStatic
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width lower or equal to the requested height and width.
            while (height / inSampleSize > reqHeight || width / inSampleSize > reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    @JvmStatic
    fun getExifOrientation(context: Context, imageUri: Uri): Int {
        var orientation = ExifInterface.ORIENTATION_UNDEFINED
        try {
            val stream = context.contentResolver.openInputStream(imageUri) ?: return orientation
            orientation = ImageHeaderParser(stream).orientation
            close(stream)
        } catch (e: IOException) {
            Log.e(TAG, "getExifOrientation: $imageUri", e)
        }
        return orientation
    }

    @JvmStatic
    fun exifToDegrees(exifOrientation: Int): Int {
        return when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    }

    @JvmStatic
    fun exifToTranslation(exifOrientation: Int): Int {
        return when (exifOrientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_FLIP_VERTICAL, ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> -1
            else -> 1
        }
    }

    /**
     * This method calculates maximum size of both width and height of bitmap.
     * It is twice the device screen diagonal for default implementation (extra quality to zoom image).
     * Size cannot exceed max texture size.
     *
     * @return - max bitmap size in pixels.
     */
    @JvmStatic
    fun calculateMaxBitmapSize(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE)
        val display: Display
        val width: Int
        val height: Int
        val size = Point()
        if (wm is WindowManager) {
            display = wm.defaultDisplay
            display.getSize(size)
        }
        width = size.x
        height = size.y

        // Twice the device screen diagonal as default
        var maxBitmapSize = Math.sqrt(Math.pow(width.toDouble(), 2.0) + Math.pow(height.toDouble(), 2.0)).toInt()

        // Check for max texture size via Canvas
        val canvas = Canvas()
        val maxCanvasSize = Math.min(canvas.maximumBitmapWidth, canvas.maximumBitmapHeight)
        if (maxCanvasSize > 0) {
            maxBitmapSize = Math.min(maxBitmapSize, maxCanvasSize)
        }

        // Check for max texture size via GL
        val maxTextureSize = EglUtils.maxTextureSize
        if (maxTextureSize > 0) {
            maxBitmapSize = Math.min(maxBitmapSize, maxTextureSize)
        }
        Log.d(TAG, "maxBitmapSize: $maxBitmapSize")
        return maxBitmapSize
    }

    @JvmStatic
    fun close(c: Closeable?) {
        if (c is Closeable) { // java.lang.IncompatibleClassChangeError: interface not implemented
            try {
                c.close()
            } catch (e: IOException) {
                // silence
            }
        }
    }
}