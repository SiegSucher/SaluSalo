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

/**
 * Reads the chosen photo, turns it upright, shrinks it so its longer side is
 * at most 512 px and re-encodes it as JPEG. Runs off the main thread. Returns
 * null when the file cannot be read as an image.
 */
suspend fun encodeProfilePhoto(context: Context, uri: Uri): ByteArray? =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        // Decode at roughly the target size first, to keep memory low.
        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= MAX_PROFILE_PHOTO_SIDE &&
            bounds.outHeight / (sample * 2) >= MAX_PROFILE_PHOTO_SIDE
        ) {
            sample *= 2
        }
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

        val scale = MAX_PROFILE_PHOTO_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val finalBitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        ByteArrayOutputStream().use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }
