package com.example.salusalo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_PROFILE_PHOTO_SIDE = 512
private const val MAX_LISTING_PHOTO_SIDE = 1600

/**
 * Reads the chosen photo, turns it upright, shrinks it so its longer side is
 * at most 512 px and re-encodes it as JPEG. Runs off the main thread. Returns
 * null when the file cannot be read as an image.
 */
suspend fun encodeProfilePhoto(context: Context, uri: Uri): ByteArray? =
    encodeJpegPhoto(context, uri, MAX_PROFILE_PHOTO_SIDE)

/** Same as [encodeProfilePhoto], for listing photos: the longer side is at most 1600 px. */
suspend fun encodeListingPhoto(context: Context, uri: Uri): ByteArray? =
    encodeJpegPhoto(context, uri, MAX_LISTING_PHOTO_SIDE)

private suspend fun encodeJpegPhoto(context: Context, uri: Uri, maxSide: Int): ByteArray? =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        // Decode at roughly the target size first, to keep memory low.
        val sample = photoSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext null

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f

        val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val finalBitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        // Free the larger decode now; several photos are encoded one after another.
        if (finalBitmap !== decoded) decoded.recycle()

        ByteArrayOutputStream().use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

/**
 * The largest power-of-two `inSampleSize` that still leaves the image's longer
 * side at least [maxSide], so decoding never loads more than needed (a 12 MP
 * 4032×3024 photo decodes at 2016×1512, not full size) and the final scaling
 * down to [maxSide] keeps full quality.
 */
internal fun photoSampleSize(width: Int, height: Int, maxSide: Int): Int {
    val longer = maxOf(width, height)
    var sample = 1
    while (longer / (sample * 2) >= maxSide) sample *= 2
    return sample
}
