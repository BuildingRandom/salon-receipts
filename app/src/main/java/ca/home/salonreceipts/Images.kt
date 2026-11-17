package ca.home.salonreceipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

object Images {
    /** Longest side of the saved photo. Claude reads receipts well at this size and the files stay small. */
    private const val MAX_SIDE = 1600

    /**
     * Reads a photo from [uri], turns it upright, shrinks it and saves it as a JPEG in [out].
     * Run off the main thread.
     */
    fun saveUprightJpeg(context: Context, uri: Uri, out: File) {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = resolver.openInputStream(uri) ?: error("Couldn't open the photo.")
        input.use { BitmapFactory.decodeStream(it, null, bounds) } // returns null by design; fills bounds
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("That file isn't a photo the app can read.")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Couldn't read the photo.")

        val degrees = try {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }

        val scale = minOf(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(degrees)
        }
        val finalBmp = if (scale == 1f && degrees == 0f) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        out.parentFile?.mkdirs()
        out.outputStream().use { finalBmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (finalBmp !== decoded) decoded.recycle()
        finalBmp.recycle()
    }

    /** Loads a small copy of a saved photo for showing on screen. */
    fun loadPreview(file: File, maxSide: Int = 1200): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
