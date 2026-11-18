package io.github.ultrapg.smartnote.capture

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import io.github.ultrapg.smartnote.data.local.AttachmentEntity
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.domain.util.TextHashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

data class StagedAttachment(
    val id: String,
    val stagedFile: File,
    val thumbnailRelativePath: String?,
    val mimeType: String,
    val originalDisplayName: String,
    val byteSize: Long,
    val widthPx: Int,
    val heightPx: Int,
    val sha256: String,
    val isScreenshot: Boolean
)

class AttachmentImporter(
    private val context: Context,
    private val attachmentStorage: AttachmentStorage
) {
    suspend fun stageIncomingUris(
        sessionId: String,
        uris: List<Uri>
    ): List<StagedAttachment> = withContext(Dispatchers.IO) {
        val sessionDir = File(attachmentStorage.incomingSharesDir, sessionId).apply { mkdirs() }
        val staged = mutableListOf<StagedAttachment>()
        for (uri in uris.take(MAX_ATTACHMENTS_PER_SHARE)) {
            stageSingleUri(sessionDir, uri)?.let(staged::add)
        }
        staged
    }

    suspend fun importUriDirectlyToNote(
        noteId: String,
        uri: Uri
    ): AttachmentEntity? = withContext(Dispatchers.IO) {
        val sessionId = "direct_${UUID.randomUUID()}"
        val sessionDir = File(attachmentStorage.incomingSharesDir, sessionId).apply { mkdirs() }
        val staged = stageSingleUri(sessionDir, uri) ?: return@withContext null
        val persisted = commitStagedAttachments(noteId, listOf(staged)).firstOrNull()
        sessionDir.deleteRecursively()
        persisted
    }

    suspend fun commitStagedAttachments(
        noteId: String,
        stagedList: List<StagedAttachment>,
        nowMs: Long = System.currentTimeMillis()
    ): List<AttachmentEntity> = withContext(Dispatchers.IO) {
        val entities = mutableListOf<AttachmentEntity>()
        for (item in stagedList) {
            val ext = extensionForMime(item.mimeType, item.originalDisplayName)
            val relativePath = "attachments/${item.id}.$ext"
            val destFile = attachmentStorage.resolvePrivateFile(relativePath)
            destFile.parentFile?.mkdirs()

            if (!item.stagedFile.renameTo(destFile)) {
                item.stagedFile.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                item.stagedFile.delete()
            }

            entities += AttachmentEntity(
                id = item.id,
                noteId = noteId,
                relativeFilePath = relativePath,
                thumbnailRelativePath = item.thumbnailRelativePath,
                mimeType = item.mimeType,
                originalDisplayName = item.originalDisplayName,
                byteSize = destFile.length(),
                widthPx = item.widthPx,
                heightPx = item.heightPx,
                sha256 = item.sha256,
                isScreenshot = item.isScreenshot,
                createdAtMs = nowMs
            )
        }
        entities
    }

    fun discardStagingSession(sessionId: String) {
        File(attachmentStorage.incomingSharesDir, sessionId).deleteRecursively()
    }

    private fun stageSingleUri(sessionDir: File, uri: Uri): StagedAttachment? {
        if (!isSafeUri(uri)) return null
        val mime = resolveMimeType(uri) ?: return null
        if (!mime.startsWith("image/", ignoreCase = true)) return null

        val displayName = resolveDisplayName(uri) ?: "image_${System.currentTimeMillis()}"
        val id = UUID.randomUUID().toString()
        val ext = extensionForMime(mime, displayName)
        val tempFile = File(sessionDir, "$id.$ext")

        var copiedBytes = 0L
        val buffer = ByteArray(64 * 1024)
        val success = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        copiedBytes += read
                        if (copiedBytes > MAX_FILE_BYTES) {
                            throw IllegalStateException("Shared file exceeds 50 MiB limit")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } != null
        }.getOrDefault(false)

        if (!success || !tempFile.exists() || copiedBytes <= 0L) {
            tempFile.delete()
            return null
        }

        val (width, height) = attachmentStorage.readImageDimensions(tempFile)
        if (width <= 0 || height <= 0) {
            tempFile.delete()
            return null
        }

        val sha256 = TextHashing.sha256Hex(tempFile)
        val thumbRel = attachmentStorage.generateThumbnail(tempFile, id)
        val looksLikeScreenshot = detectScreenshotHeuristic(displayName, mime, width, height)

        return StagedAttachment(
            id = id,
            stagedFile = tempFile,
            thumbnailRelativePath = thumbRel,
            mimeType = mime,
            originalDisplayName = displayName,
            byteSize = copiedBytes,
            widthPx = width,
            heightPx = height,
            sha256 = sha256,
            isScreenshot = looksLikeScreenshot
        )
    }

    internal fun isSafeUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme == "content") return true
        if (scheme == "file") {
            val rawPath = uri.path ?: return false
            val targetCanonical = runCatching { File(rawPath).canonicalPath }.getOrNull() ?: return false
            val appDataCanonical = runCatching { context.filesDir.parentFile?.canonicalPath }.getOrNull()
            // Reject file:// URIs targeting SmartNote's own private app directory
            if (appDataCanonical != null &&
                (targetCanonical == appDataCanonical || targetCanonical.startsWith("$appDataCanonical${File.separator}"))
            ) {
                return false
            }
            return true
        }
        return false
    }

    private fun resolveMimeType(uri: Uri): String? {
        val direct = context.contentResolver.getType(uri)
        if (!direct.isNullOrBlank() && direct != "*/*") return direct
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase(Locale.ROOT)
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
        }
        return uri.lastPathSegment
    }

    private fun extensionForMime(mime: String, displayName: String): String {
        return when (mime.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> displayName.substringAfterLast('.', "").takeIf { it.length in 2..5 } ?: "png"
        }
    }

    private fun detectScreenshotHeuristic(
        displayName: String,
        mime: String,
        width: Int,
        height: Int
    ): Boolean {
        val lower = displayName.lowercase(Locale.ROOT)
        if ("screenshot" in lower || "screen_" in lower || "screencap" in lower) return true
        val aspect = if (width > 0) height.toFloat() / width.toFloat() else 1f
        return mime.equals("image/png", ignoreCase = true) && (aspect >= 1.75f || aspect <= 0.6f)
    }

    companion object {
        const val MAX_FILE_BYTES = 50L * 1024L * 1024L
        const val MAX_ATTACHMENTS_PER_SHARE = 16
    }
}
