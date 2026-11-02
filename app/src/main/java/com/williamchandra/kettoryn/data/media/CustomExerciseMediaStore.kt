package com.williamchandra.kettoryn.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** User-picked images are sampled, visibly center-cropped, stripped of metadata and stored privately. */
class CustomExerciseMediaStore(private val context: Context) {
    val directory: File get() = File(context.filesDir, "custom-exercise-images").apply { mkdirs() }
    fun file(key: String?): File? = key?.takeIf(::isValidKey)?.let { File(directory, it) }?.takeIf { it.isFile }

    fun import(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val size = stream.read(buffer); if (size < 0) break
                require(out.size() + size <= MAX_SOURCE_BYTES) { "Choose an image smaller than 20 MB." }
                out.write(buffer, 0, size)
            }
            out.toByteArray()
        } ?: error("The image provider could not open this photo.")
        return storeEncodedSource(bytes)
    }

    fun storeEncodedSource(bytes: ByteArray): String {
        require(bytes.size <= MAX_SOURCE_BYTES) { "Choose an image smaller than 20 MB." }
        val encoded = squareImage(bytes)
        val key = sha256(encoded) + ".webp"
        val target = File(directory, key)
        if (!target.exists()) {
            val temporary = File.createTempFile("crop-", ".tmp", directory)
            try { temporary.writeBytes(encoded); check(temporary.renameTo(target)) { "Unable to save image." } }
            finally { temporary.delete() }
        }
        return key
    }

    companion object {
        const val MAX_SOURCE_BYTES = 20 * 1024 * 1024
        const val MAX_MEDIA_BYTES = 3 * 1024 * 1024
        fun isValidKey(key: String): Boolean = Regex("[a-f0-9]{64}\\.webp").matches(key)
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun squareImage(bytes: ByteArray): ByteArray {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..30000 && bounds.outHeight in 1..30000 && bounds.outWidth.toLong() * bounds.outHeight <= 300_000_000) { "Choose a supported photo with valid dimensions." }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1536) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("This image could not be decoded.")
            val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { postScale(-1f, 1f); postRotate(90f) }
                    ExifInterface.ORIENTATION_TRANSVERSE -> { postScale(-1f, 1f); postRotate(270f) }
                }
            }
            val oriented = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val side = minOf(oriented.width, oriented.height)
            val crop = Bitmap.createBitmap(oriented, (oriented.width - side) / 2, (oriented.height - side) / 2, side, side)
            val scaled = if (side > 768) Bitmap.createScaledBitmap(crop, 768, 768, true) else crop
            val out = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
            check(scaled.compress(format, 95, out)) { "Unable to prepare photo." }
            val result = out.toByteArray()
            listOf(scaled, crop, oriented, decoded).distinct().forEach { it.recycle() }
            require(result.size <= MAX_MEDIA_BYTES) { "This photo is too detailed. Choose a smaller image." }
            return result
        }
    }
}
