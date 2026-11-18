package io.github.ultrapg.smartnote.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class AttachmentStorage(
    private val context: Context
) {
    val attachmentsDir: File
        get() = File(context.filesDir, "attachments").apply { mkdirs() }

    val thumbnailsDir: File
        get() = File(context.filesDir, "thumbnails").apply { mkdirs() }

    val incomingSharesDir: File
        get() = File(context.cacheDir, "incoming_shares").apply { mkdirs() }

    fun resolvePrivateFile(relativePath: String): File {
        val target = File(context.filesDir, relativePath).canonicalFile
        val root = context.filesDir.canonicalFile
        require(target.path.startsWith(root.path + File.separator)) {
            "Invalid relative attachment path: $relativePath"
        }
        return target
    }

    fun generateThumbnail(sourceFile: File, attachmentId: String, maxDimensionPx: Int = 512): String? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return null

            var sampleSize = 1
            val longest = max(width, height)
            while (longest / (sampleSize * 2) >= maxDimensionPx) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampled = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts) ?: return null
            val scaled = if (max(sampled.width, sampled.height) > maxDimensionPx) {
                val ratio = maxDimensionPx.toFloat() / max(sampled.width, sampled.height).toFloat()
                val targetW = (sampled.width * ratio).roundToInt().coerceAtLeast(1)
                val targetH = (sampled.height * ratio).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(sampled, targetW, targetH, true).also {
                    if (it !== sampled) sampled.recycle()
                }
            } else {
                sampled
            }

            val relativePath = "thumbnails/${attachmentId}_thumb.webp"
            val destFile = resolvePrivateFile(relativePath)
            destFile.parentFile?.mkdirs()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            FileOutputStream(destFile).use { out ->
                scaled.compress(format, 82, out)
            }
            scaled.recycle()
            relativePath
        }.getOrNull()
    }

    fun readImageDimensions(file: File): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
    }

    fun deleteRelativeFileQuietly(relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        runCatching {
            val f = resolvePrivateFile(relativePath)
            if (f.exists()) f.delete()
        }
    }

    fun directorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun databaseSizeBytes(): Long {
        val dbFile = context.getDatabasePath(SmartNoteDatabase.DB_NAME)
        val walFile = File("${dbFile.absolutePath}-wal")
        val shmFile = File("${dbFile.absolutePath}-shm")
        return listOf(dbFile, walFile, shmFile).filter { it.exists() }.sumOf { it.length() }
    }

    fun deleteStaleIncomingShares(maxAgeMs: Long = 24 * 60 * 60_000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        incomingSharesDir.listFiles()?.forEach { child ->
            if (child.lastModified() < cutoff) {
                child.deleteRecursively()
            }
        }
    }
}
