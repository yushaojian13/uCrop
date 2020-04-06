package com.yalantis.ucrop.task

import android.Manifest.permission
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import com.yalantis.ucrop.callback.BitmapLoadCallback
import com.yalantis.ucrop.model.ExifInfo
import com.yalantis.ucrop.task.BitmapLoadTask.BitmapWorkerResult
import com.yalantis.ucrop.util.BitmapLoadUtils.calculateInSampleSize
import com.yalantis.ucrop.util.BitmapLoadUtils.close
import com.yalantis.ucrop.util.BitmapLoadUtils.exifToDegrees
import com.yalantis.ucrop.util.BitmapLoadUtils.exifToTranslation
import com.yalantis.ucrop.util.BitmapLoadUtils.getExifOrientation
import com.yalantis.ucrop.util.BitmapLoadUtils.transformBitmap
import com.yalantis.ucrop.util.FileUtils.getPath
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import okio.Okio
import okio.Sink
import java.io.*

/**
 * Creates and returns a Bitmap for a given Uri(String url).
 * inSampleSize is calculated based on requiredWidth property. However can be adjusted if OOM occurs.
 * If any EXIF config is found - bitmap is transformed properly.
 */
class BitmapLoadTask(private val mContext: Context,
                     private var mInputUri: Uri?, private val mOutputUri: Uri?,
                     private val mRequiredWidth: Int, private val mRequiredHeight: Int,
                     private val mBitmapLoadCallback: BitmapLoadCallback) : AsyncTask<Void, Void, BitmapWorkerResult>() {

    class BitmapWorkerResult {
        var mBitmapResult: Bitmap? = null
        var mExifInfo: ExifInfo? = null
        var mBitmapWorkerException: Exception? = null

        constructor(bitmapResult: Bitmap, exifInfo: ExifInfo) {
            mBitmapResult = bitmapResult
            mExifInfo = exifInfo
        }

        constructor(bitmapWorkerException: Exception) {
            mBitmapWorkerException = bitmapWorkerException
        }
    }

    override fun doInBackground(vararg params: Void): BitmapWorkerResult {
        val inputUri = mInputUri ?: return BitmapWorkerResult(NullPointerException("Input Uri cannot be null"))

        try {
            processInputUri(inputUri)
        } catch (e: NullPointerException) {
            return BitmapWorkerResult(e)
        } catch (e: IOException) {
            return BitmapWorkerResult(e)
        }

        val parcelFileDescriptor: ParcelFileDescriptor?
        parcelFileDescriptor = try {
            mContext.contentResolver.openFileDescriptor(inputUri, "r")
        } catch (e: FileNotFoundException) {
            return BitmapWorkerResult(e)
        }

        val fileDescriptor: FileDescriptor
        fileDescriptor = if (parcelFileDescriptor != null) {
            parcelFileDescriptor.fileDescriptor
        } else {
            return BitmapWorkerResult(NullPointerException("ParcelFileDescriptor was null for given Uri: [$inputUri]"))
        }

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options)
        if (options.outWidth == -1 || options.outHeight == -1) {
            return BitmapWorkerResult(IllegalArgumentException("Bounds for bitmap could not be retrieved from the Uri: [$inputUri]"))
        }

        options.inSampleSize = calculateInSampleSize(options, mRequiredWidth, mRequiredHeight)
        options.inJustDecodeBounds = false

        var decodeSampledBitmap: Bitmap? = null

        var decodeAttemptSuccess = false
        while (!decodeAttemptSuccess) {
            try {
                decodeSampledBitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options)
                decodeAttemptSuccess = true
            } catch (error: OutOfMemoryError) {
                Log.e(TAG, "doInBackground: BitmapFactory.decodeFileDescriptor: ", error)
                options.inSampleSize *= 2
            }
        }

        if (decodeSampledBitmap == null) {
            return BitmapWorkerResult(IllegalArgumentException("Bitmap could not be decoded from the Uri: [$inputUri]"))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            close(parcelFileDescriptor)
        }

        val exifOrientation = getExifOrientation(mContext, inputUri)
        val exifDegrees = exifToDegrees(exifOrientation)
        val exifTranslation = exifToTranslation(exifOrientation)

        val exifInfo = ExifInfo(exifOrientation, exifDegrees, exifTranslation)

        val matrix = Matrix()
        if (exifDegrees != 0) {
            matrix.preRotate(exifDegrees.toFloat())
        }
        if (exifTranslation != 1) {
            matrix.postScale(exifTranslation.toFloat(), 1f)
        }

        return if (!matrix.isIdentity) {
            BitmapWorkerResult(transformBitmap(decodeSampledBitmap, matrix), exifInfo)
        } else BitmapWorkerResult(decodeSampledBitmap, exifInfo)
    }

    @Throws(NullPointerException::class, IOException::class)
    private fun processInputUri(inputUri: Uri) {
        val inputUriScheme = inputUri.scheme
        Log.d(TAG, "Uri scheme: $inputUriScheme")
        if ("http" == inputUriScheme || "https" == inputUriScheme) {
            try {
                downloadFile(inputUri, mOutputUri)
            } catch (e: NullPointerException) {
                Log.e(TAG, "Downloading failed", e)
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Downloading failed", e)
                throw e
            }
        } else if ("content" == inputUriScheme) {
            val path = filePath
            if (!TextUtils.isEmpty(path) && File(path).exists()) {
                mInputUri = Uri.fromFile(File(path))
            } else {
                try {
                    copyFile(inputUri, mOutputUri)
                } catch (e: NullPointerException) {
                    Log.e(TAG, "Copying failed", e)
                    throw e
                } catch (e: IOException) {
                    Log.e(TAG, "Copying failed", e)
                    throw e
                }
            }
        } else if ("file" != inputUriScheme) {
            Log.e(TAG, "Invalid Uri scheme $inputUriScheme")
            throw IllegalArgumentException("Invalid Uri scheme$inputUriScheme")
        }
    }

    private val filePath: String?
        get() = if (ContextCompat.checkSelfPermission(mContext, permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            getPath(mContext, mInputUri!!)
        } else {
            null
        }

    @Throws(NullPointerException::class, IOException::class)
    private fun copyFile(inputUri: Uri, outputUri: Uri?) {
        Log.d(TAG, "copyFile")

        if (outputUri == null) {
            throw NullPointerException("Output Uri is null - cannot copy image")
        }

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = mContext.contentResolver.openInputStream(inputUri)
            outputStream = FileOutputStream(File(outputUri.path))
            if (inputStream == null) {
                throw NullPointerException("InputStream for given input Uri is null")
            }

            inputStream.copyTo(outputStream)
        } finally {
            close(outputStream)
            close(inputStream)

            // swap uris, because input image was copied to the output destination
            // (cropped image will override it later)
            mInputUri = mOutputUri
        }
    }

    @Throws(NullPointerException::class, IOException::class)
    private fun downloadFile(inputUri: Uri, outputUri: Uri?) {
        Log.d(TAG, "downloadFile")

        if (outputUri == null) {
            throw NullPointerException("Output Uri is null - cannot download image")
        }

        val client = OkHttpClient()

        var source: BufferedSource? = null
        var sink: Sink? = null
        var response: Response? = null
        try {
            val request = Request.Builder()
                    .url(inputUri.toString())
                    .build()
            response = client.newCall(request).execute()
            source = response.body()!!.source()

            val outputStream = mContext.contentResolver.openOutputStream(outputUri)
            if (outputStream != null) {
                sink = Okio.sink(outputStream)
                source.readAll(sink!!)
            } else {
                throw NullPointerException("OutputStream for given output Uri is null")
            }
        } finally {
            close(source)
            close(sink)
            if (response != null) {
                close(response.body())
            }
            client.dispatcher().cancelAll()

            // swap uris, because input image was downloaded to the output destination
            // (cropped image will override it later)
            mInputUri = mOutputUri
        }
    }

    override fun onPostExecute(result: BitmapWorkerResult) {
        val bitmapWorkerException = result.mBitmapWorkerException
        val imageInputPath = mInputUri?.path
        val bitmapResult = result.mBitmapResult
        val exifInfo = result.mExifInfo
        val imageOutputPath = mOutputUri?.path
        if (bitmapResult != null && exifInfo != null && !imageInputPath.isNullOrEmpty()) {
            mBitmapLoadCallback.onBitmapLoaded(bitmapResult, exifInfo, imageInputPath, imageOutputPath)
        } else {
            mBitmapLoadCallback.onFailure(bitmapWorkerException ?: Exception("unknown"))
        }
    }

    companion object {
        private const val TAG = "BitmapWorkerTask"
    }
}