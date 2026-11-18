package io.github.ultrapg.smartnote.backup

import android.content.Context
import android.net.Uri
import io.github.ultrapg.smartnote.BuildConfig
import io.github.ultrapg.smartnote.data.local.AttachmentEntity
import io.github.ultrapg.smartnote.data.local.LinkPreviewEntity
import io.github.ultrapg.smartnote.data.local.NoteBlockEntity
import io.github.ultrapg.smartnote.data.local.NoteEntity
import io.github.ultrapg.smartnote.data.local.NoteTagCrossRefEntity
import io.github.ultrapg.smartnote.data.local.OcrResultEntity
import io.github.ultrapg.smartnote.data.local.ReminderEntity
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.local.TagEntity
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.domain.util.TextHashing
import io.github.ultrapg.smartnote.reminder.ReminderScheduler
import io.github.ultrapg.smartnote.search.SearchIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupManifest(
    val format: String = "smartnote-backup",
    val schemaVersion: Int = 1,
    val appVersionName: String,
    val appVersionCode: Int,
    val createdAtEpochMs: Long,
    val noteCount: Int,
    val tagCount: Int,
    val attachmentCount: Int,
    val reminderCount: Int,
    val dataSha256: String,
    val attachmentChecksums: Map<String, String>
)

@Serializable
data class BackupSnapshot(
    val schemaVersion: Int = 1,
    val notes: List<BackupNoteDto>,
    val blocks: List<BackupBlockDto>,
    val tags: List<BackupTagDto>,
    val noteTags: List<BackupNoteTagDto>,
    val attachments: List<BackupAttachmentDto>,
    val ocrResults: List<BackupOcrDto>,
    val links: List<BackupLinkDto>,
    val reminders: List<BackupReminderDto>
)

@Serializable
data class BackupNoteDto(
    val id: String,
    val title: String,
    val plainTextPreview: String,
    val fullPlainText: String,
    val wordCount: Int,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isDeleted: Boolean,
    val deletedAtMs: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val contentRevision: Long,
    val hasChecklist: Boolean,
    val checklistCheckedCount: Int,
    val checklistTotalCount: Int,
    val primaryAttachmentId: String? = null,
    val contentSha256: String,
    val simhash64: Long,
    val sourceType: String
)

@Serializable
data class BackupBlockDto(
    val id: String,
    val noteId: String,
    val position: Int,
    val blockType: String,
    val textContent: String,
    val isChecked: Boolean,
    val indentLevel: Int,
    val attachmentId: String? = null,
    val linkUrl: String? = null
)

@Serializable
data class BackupTagDto(
    val id: String,
    val name: String,
    val normalizedName: String,
    val colorSeed: Int,
    val createdAtMs: Long
)

@Serializable
data class BackupNoteTagDto(
    val noteId: String,
    val tagId: String,
    val source: String,
    val confidence: Float,
    val isDismissed: Boolean,
    val updatedAtMs: Long
)

@Serializable
data class BackupAttachmentDto(
    val id: String,
    val noteId: String,
    val relativeFilePath: String,
    val mimeType: String,
    val originalDisplayName: String,
    val byteSize: Long,
    val widthPx: Int,
    val heightPx: Int,
    val sha256: String,
    val isScreenshot: Boolean,
    val createdAtMs: Long
)

@Serializable
data class BackupOcrDto(
    val attachmentId: String,
    val recognizedText: String,
    val languageCode: String,
    val avgConfidence: Float,
    val status: String,
    val processedAtMs: Long,
    val engineName: String
)

@Serializable
data class BackupLinkDto(
    val id: String,
    val noteId: String,
    val rawUrl: String,
    val canonicalUrl: String,
    val domain: String,
    val pageTitle: String? = null,
    val description: String? = null,
    val fetchStatus: String,
    val updatedAtMs: Long
)

@Serializable
data class BackupReminderDto(
    val id: String,
    val noteId: String,
    val triggerAtMs: Long,
    val zoneId: String,
    val localDateTimeIso: String,
    val repeatRule: String,
    val isCompleted: Boolean,
    val completedAtMs: Long? = null,
    val snoozeCount: Int,
    val useLiveUpdate: Boolean
)

enum class RestoreMode {
    MERGE,
    REPLACE_ALL
}

data class BackupExportResult(
    val noteCount: Int,
    val attachmentCount: Int,
    val bytesWritten: Long
)

data class BackupRestoreResult(
    val notesImported: Int,
    val notesSkippedOrMerged: Int,
    val attachmentsRestored: Int
)

object SafeZipExtractor {
    private const val MAX_ENTRIES = 10_000
    private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L

    fun extractValidated(zipStream: ZipInputStream, destDir: File) {
        destDir.mkdirs()
        val canonicalRoot = destDir.canonicalFile
        var entryCount = 0
        var totalBytes = 0L
        val buffer = ByteArray(32 * 1024)

        while (true) {
            val entry: ZipEntry = zipStream.nextEntry ?: break
            entryCount++
            if (entryCount > MAX_ENTRIES) {
                throw SecurityException("Backup archive exceeds maximum entry count ($MAX_ENTRIES).")
            }
            val cleanName = entry.name.replace('\\', '/')
            if (cleanName.startsWith("/") || cleanName.contains("..")) {
                throw SecurityException("Unsafe archive path rejected: ${entry.name}")
            }
            val outFile = File(canonicalRoot, cleanName).canonicalFile
            if (!outFile.path.startsWith(canonicalRoot.path + File.separator) && outFile != canonicalRoot) {
                throw SecurityException("Zip-Slip traversal attempt blocked: ${entry.name}")
            }

            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                var entryBytes = 0L
                FileOutputStream(outFile).use { out ->
                    while (true) {
                        val read = zipStream.read(buffer)
                        if (read <= 0) break
                        entryBytes += read
                        totalBytes += read
                        if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                            throw SecurityException("Backup archive exceeds safe uncompressed size limit.")
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
            zipStream.closeEntry()
        }
    }
}

class BackupManager(
    private val context: Context,
    private val database: SmartNoteDatabase,
    private val attachmentStorage: AttachmentStorage,
    private val searchIndexer: SearchIndexer,
    private val reminderScheduler: ReminderScheduler
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportToUri(destinationUri: Uri): BackupExportResult = withContext(Dispatchers.IO) {
        val rawOut = context.contentResolver.openOutputStream(destinationUri)
            ?: error("Unable to open backup destination stream.")

        val notes = database.noteDao().getAllNotesSnapshot()
        val blocks = database.noteBlockDao().getAllBlocksSnapshot()
        val tags = database.tagDao().getAllTagsSnapshot()
        val crossRefs = database.tagDao().getAllCrossRefsSnapshot()
        val attachments = database.attachmentDao().getAllAttachmentsSnapshot()
        val ocrs = database.attachmentDao().getAllOcrResultsSnapshot()
        val links = database.linkPreviewDao().getAllLinksSnapshot()
        val reminders = database.reminderDao().getAllRemindersSnapshot()

        val snapshot = BackupSnapshot(
            notes = notes.map {
                BackupNoteDto(
                    id = it.id,
                    title = it.title,
                    plainTextPreview = it.plainTextPreview,
                    fullPlainText = it.fullPlainText,
                    wordCount = it.wordCount,
                    isPinned = it.isPinned,
                    isArchived = it.isArchived,
                    isDeleted = it.isDeleted,
                    deletedAtMs = it.deletedAtMs,
                    createdAtMs = it.createdAtMs,
                    updatedAtMs = it.updatedAtMs,
                    contentRevision = it.contentRevision,
                    hasChecklist = it.hasChecklist,
                    checklistCheckedCount = it.checklistCheckedCount,
                    checklistTotalCount = it.checklistTotalCount,
                    primaryAttachmentId = it.primaryAttachmentId,
                    contentSha256 = it.contentSha256,
                    simhash64 = it.simhash64,
                    sourceType = it.sourceType
                )
            },
            blocks = blocks.map {
                BackupBlockDto(
                    id = it.id,
                    noteId = it.noteId,
                    position = it.position,
                    blockType = it.blockType,
                    textContent = it.textContent,
                    isChecked = it.isChecked,
                    indentLevel = it.indentLevel,
                    attachmentId = it.attachmentId,
                    linkUrl = it.linkUrl
                )
            },
            tags = tags.map {
                BackupTagDto(it.id, it.name, it.normalizedName, it.colorSeed, it.createdAtMs)
            },
            noteTags = crossRefs.map {
                BackupNoteTagDto(it.noteId, it.tagId, it.source, it.confidence, it.isDismissed, it.updatedAtMs)
            },
            attachments = attachments.map {
                BackupAttachmentDto(
                    id = it.id,
                    noteId = it.noteId,
                    relativeFilePath = it.relativeFilePath,
                    mimeType = it.mimeType,
                    originalDisplayName = it.originalDisplayName,
                    byteSize = it.byteSize,
                    widthPx = it.widthPx,
                    heightPx = it.heightPx,
                    sha256 = it.sha256,
                    isScreenshot = it.isScreenshot,
                    createdAtMs = it.createdAtMs
                )
            },
            ocrResults = ocrs.map {
                BackupOcrDto(
                    attachmentId = it.attachmentId,
                    recognizedText = it.recognizedText,
                    languageCode = it.languageCode,
                    avgConfidence = it.avgConfidence,
                    status = it.status,
                    processedAtMs = it.processedAtMs,
                    engineName = it.engineName
                )
            },
            links = links.map {
                BackupLinkDto(
                    id = it.id,
                    noteId = it.noteId,
                    rawUrl = it.rawUrl,
                    canonicalUrl = it.canonicalUrl,
                    domain = it.domain,
                    pageTitle = it.pageTitle,
                    description = it.description,
                    fetchStatus = it.fetchStatus,
                    updatedAtMs = it.updatedAtMs
                )
            },
            reminders = reminders.map {
                BackupReminderDto(
                    id = it.id,
                    noteId = it.noteId,
                    triggerAtMs = it.triggerAtMs,
                    zoneId = it.zoneId,
                    localDateTimeIso = it.localDateTimeIso,
                    repeatRule = it.repeatRule,
                    isCompleted = it.isCompleted,
                    completedAtMs = it.completedAtMs,
                    snoozeCount = it.snoozeCount,
                    useLiveUpdate = it.useLiveUpdate
                )
            }
        )

        val dataBytes = json.encodeToString(BackupSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)
        val dataSha256 = TextHashing.sha256Hex(dataBytes)

        val attachmentChecksums = LinkedHashMap<String, String>()
        val exportFiles = LinkedHashMap<String, File>()
        for (att in attachments) {
            val file = attachmentStorage.resolvePrivateFile(att.relativeFilePath)
            if (file.exists() && file.isFile) {
                exportFiles[att.relativeFilePath] = file
                attachmentChecksums[att.relativeFilePath] = TextHashing.sha256Hex(file)
            }
        }

        val manifest = BackupManifest(
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            createdAtEpochMs = System.currentTimeMillis(),
            noteCount = notes.size,
            tagCount = tags.size,
            attachmentCount = exportFiles.size,
            reminderCount = reminders.size,
            dataSha256 = dataSha256,
            attachmentChecksums = attachmentChecksums
        )
        val manifestBytes = json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)

        var totalBytesWritten = 0L
        ZipOutputStream(BufferedOutputStream(rawOut)).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()
            totalBytesWritten += manifestBytes.size

            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(dataBytes)
            zos.closeEntry()
            totalBytesWritten += dataBytes.size

            for ((relPath, file) in exportFiles) {
                zos.putNextEntry(ZipEntry(relPath))
                file.inputStream().use { input ->
                    totalBytesWritten += input.copyTo(zos, bufferSize = 32 * 1024)
                }
                zos.closeEntry()
            }
        }

        BackupExportResult(
            noteCount = notes.size,
            attachmentCount = exportFiles.size,
            bytesWritten = totalBytesWritten
        )
    }

    suspend fun restoreFromUri(sourceUri: Uri, mode: RestoreMode): BackupRestoreResult = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "restore_staging_${UUID.randomUUID()}")
        try {
            val rawIn = context.contentResolver.openInputStream(sourceUri)
                ?: error("Unable to open backup file.")
            ZipInputStream(BufferedInputStream(rawIn)).use { zis ->
                SafeZipExtractor.extractValidated(zis, stagingDir)
            }

            val manifestFile = File(stagingDir, "manifest.json")
            val dataFile = File(stagingDir, "data.json")
            require(manifestFile.exists() && dataFile.exists()) {
                "Invalid SmartNote backup: missing manifest.json or data.json."
            }

            val manifest = json.decodeFromString(BackupManifest.serializer(), manifestFile.readText())
            require(manifest.format == "smartnote-backup" && manifest.schemaVersion in 1..1) {
                "Unsupported SmartNote backup format or schema version (${manifest.schemaVersion})."
            }

            val actualDataSha = TextHashing.sha256Hex(dataFile)
            require(actualDataSha == manifest.dataSha256) {
                "Backup data.json checksum mismatch (archive corrupted)."
            }

            // Verify every declared attachment checksum before altering database
            val stagedAttachmentsRoot = stagingDir.canonicalFile
            for ((relPath, expectedSha) in manifest.attachmentChecksums) {
                val stagedAtt = File(stagedAttachmentsRoot, relPath).canonicalFile
                require(stagedAtt.path.startsWith(stagedAttachmentsRoot.path + File.separator) && stagedAtt.exists()) {
                    "Missing attachment in backup: $relPath"
                }
                val actualAttSha = TextHashing.sha256Hex(stagedAtt)
                require(actualAttSha == expectedSha) {
                    "Checksum mismatch for attachment $relPath"
                }
            }

            val snapshot = json.decodeFromString(BackupSnapshot.serializer(), dataFile.readText())
            val result = applyValidatedSnapshot(snapshot, stagedAttachmentsRoot, mode)
            searchIndexer.rebuildAll()
            reminderScheduler.rescheduleAllActiveReminders(recomputeTimeZone = true)
            result
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private suspend fun applyValidatedSnapshot(
        snapshot: BackupSnapshot,
        stagingRoot: File,
        mode: RestoreMode
    ): BackupRestoreResult {
        var importedCount = 0
        var mergedOrSkipped = 0
        var attachmentsRestored = 0

        if (mode == RestoreMode.REPLACE_ALL) {
            val oldAttachments = attachmentStorage.attachmentsDir.listFiles()?.toList().orEmpty()
            val oldThumbs = attachmentStorage.thumbnailsDir.listFiles()?.toList().orEmpty()

            database.inWriteTransaction {
                database.noteDao().deleteAllNotes()
                database.tagDao().deleteAllTags()

                database.noteDao().upsertNotes(snapshot.notes.map { it.toEntity() })
                database.noteBlockDao().insertBlocks(snapshot.blocks.map { it.toEntity() })
                database.tagDao().upsertTags(snapshot.tags.map { it.toEntity() })
                database.tagDao().upsertCrossRefs(snapshot.noteTags.map { it.toEntity() })

                val restoredAttEntities = mutableListOf<AttachmentEntity>()
                for (att in snapshot.attachments) {
                    val stagedFile = File(stagingRoot, att.relativeFilePath)
                    val destFile = attachmentStorage.resolvePrivateFile(att.relativeFilePath)
                    var thumbPath: String? = null
                    if (stagedFile.exists()) {
                        destFile.parentFile?.mkdirs()
                        stagedFile.copyTo(destFile, overwrite = true)
                        thumbPath = attachmentStorage.generateThumbnail(destFile, att.id)
                        attachmentsRestored++
                    }
                    restoredAttEntities += att.toEntity(thumbPath)
                }
                if (restoredAttEntities.isNotEmpty()) {
                    database.attachmentDao().upsertAttachments(restoredAttEntities)
                }
                if (snapshot.ocrResults.isNotEmpty()) {
                    database.attachmentDao().upsertOcrResults(snapshot.ocrResults.map { it.toEntity() })
                }
                if (snapshot.links.isNotEmpty()) {
                    database.linkPreviewDao().upsertLinks(snapshot.links.map { it.toEntity() })
                }
                if (snapshot.reminders.isNotEmpty()) {
                    database.reminderDao().upsertReminders(snapshot.reminders.map { it.toEntity() })
                }
                importedCount = snapshot.notes.size
            }

            // Delete old attachment files not present in restored snapshot
            val keptPaths = snapshot.attachments.map { it.relativeFilePath }.toSet()
            oldAttachments.forEach { file ->
                if ("attachments/${file.name}" !in keptPaths) file.delete()
            }
            val keptThumbIds = snapshot.attachments.map { "${it.id}_thumb.webp" }.toSet()
            oldThumbs.forEach { file ->
                if (file.name !in keptThumbIds) file.delete()
            }
        } else {
            // Safe MERGE mode
            val existingNotes = database.noteDao().getAllNotesSnapshot().associateBy { it.id }
            val existingTagsByNorm = database.tagDao().getAllTagsSnapshot().associateBy { it.normalizedName }

            val blocksByNote = snapshot.blocks.groupBy { it.noteId }
            val crossRefsByNote = snapshot.noteTags.groupBy { it.noteId }
            val tagById = snapshot.tags.associateBy { it.id }
            val attsByNote = snapshot.attachments.groupBy { it.noteId }
            val ocrByAttId = snapshot.ocrResults.associateBy { it.attachmentId }
            val linksByNote = snapshot.links.groupBy { it.noteId }
            val reminderByNote = snapshot.reminders.associateBy { it.noteId }

            database.inWriteTransaction {
                // Ensure tags exist without violating unique normalized_name
                val tagIdRemap = mutableMapOf<String, String>()
                for (t in snapshot.tags) {
                    val match = existingTagsByNorm[t.normalizedName]
                    if (match != null) {
                        tagIdRemap[t.id] = match.id
                    } else {
                        database.tagDao().upsertTag(t.toEntity())
                        tagIdRemap[t.id] = t.id
                    }
                }

                for (backupNote in snapshot.notes) {
                    val existing = existingNotes[backupNote.id]
                    if (existing != null && existing.contentSha256 == backupNote.contentSha256) {
                        mergedOrSkipped++
                        continue
                    }

                    val targetNoteId = if (existing == null) {
                        importedCount++
                        backupNote.id
                    } else {
                        // Preserve both notes if UUID collided with differing content!
                        mergedOrSkipped++
                        UUID.randomUUID().toString()
                    }
                    val noteEntity = backupNote.toEntity().copy(
                        id = targetNoteId,
                        title = if (existing != null) "${backupNote.title} (Restored)" else backupNote.title
                    )
                    database.noteDao().upsertNote(noteEntity)

                    val attIdRemap = mutableMapOf<String, String>()
                    val restoredAtts = mutableListOf<AttachmentEntity>()
                    val restoredOcrs = mutableListOf<OcrResultEntity>()
                    for (att in attsByNote[backupNote.id].orEmpty()) {
                        val newAttId = if (targetNoteId == backupNote.id) att.id else UUID.randomUUID().toString()
                        attIdRemap[att.id] = newAttId
                        val ext = att.relativeFilePath.substringAfterLast('.', "png")
                        val newRelPath = "attachments/$newAttId.$ext"
                        val stagedFile = File(stagingRoot, att.relativeFilePath)
                        val destFile = attachmentStorage.resolvePrivateFile(newRelPath)
                        var thumbPath: String? = null
                        if (stagedFile.exists()) {
                            destFile.parentFile?.mkdirs()
                            stagedFile.copyTo(destFile, overwrite = true)
                            thumbPath = attachmentStorage.generateThumbnail(destFile, newAttId)
                            attachmentsRestored++
                        }
                        restoredAtts += att.toEntity(thumbPath).copy(
                            id = newAttId,
                            noteId = targetNoteId,
                            relativeFilePath = newRelPath
                        )
                        ocrByAttId[att.id]?.let { ocr ->
                            restoredOcrs += ocr.toEntity().copy(attachmentId = newAttId)
                        }
                    }

                    val mappedBlocks = blocksByNote[backupNote.id].orEmpty().map { blk ->
                        blk.toEntity().copy(
                            id = if (targetNoteId == backupNote.id) blk.id else UUID.randomUUID().toString(),
                            noteId = targetNoteId,
                            attachmentId = blk.attachmentId?.let { attIdRemap[it] ?: it }
                        )
                    }
                    database.noteBlockDao().insertBlocks(mappedBlocks)

                    if (restoredAtts.isNotEmpty()) database.attachmentDao().upsertAttachments(restoredAtts)
                    if (restoredOcrs.isNotEmpty()) database.attachmentDao().upsertOcrResults(restoredOcrs)

                    val mappedCrossRefs = crossRefsByNote[backupNote.id].orEmpty().mapNotNull { ref ->
                        val resolvedTagId = tagIdRemap[ref.tagId] ?: tagById[ref.tagId]?.id ?: return@mapNotNull null
                        ref.toEntity().copy(noteId = targetNoteId, tagId = resolvedTagId)
                    }
                    if (mappedCrossRefs.isNotEmpty()) database.tagDao().upsertCrossRefs(mappedCrossRefs)

                    val mappedLinks = linksByNote[backupNote.id].orEmpty().map { l ->
                        l.toEntity().copy(
                            id = if (targetNoteId == backupNote.id) l.id else UUID.randomUUID().toString(),
                            noteId = targetNoteId
                        )
                    }
                    if (mappedLinks.isNotEmpty()) database.linkPreviewDao().upsertLinks(mappedLinks)

                    reminderByNote[backupNote.id]?.let { rem ->
                        database.reminderDao().upsertReminder(
                            rem.toEntity().copy(
                                id = if (targetNoteId == backupNote.id) rem.id else UUID.randomUUID().toString(),
                                noteId = targetNoteId
                            )
                        )
                    }
                }
            }
        }

        return BackupRestoreResult(importedCount, mergedOrSkipped, attachmentsRestored)
    }

    private fun BackupNoteDto.toEntity() = NoteEntity(
        id = id, title = title, plainTextPreview = plainTextPreview, fullPlainText = fullPlainText,
        wordCount = wordCount, isPinned = isPinned, isArchived = isArchived, isDeleted = isDeleted,
        deletedAtMs = deletedAtMs, createdAtMs = createdAtMs, updatedAtMs = updatedAtMs,
        contentRevision = contentRevision, hasChecklist = hasChecklist,
        checklistCheckedCount = checklistCheckedCount, checklistTotalCount = checklistTotalCount,
        primaryAttachmentId = primaryAttachmentId, contentSha256 = contentSha256,
        simhash64 = simhash64, sourceType = sourceType
    )

    private fun BackupBlockDto.toEntity() = NoteBlockEntity(
        id = id, noteId = noteId, position = position, blockType = blockType,
        textContent = textContent, isChecked = isChecked, indentLevel = indentLevel,
        attachmentId = attachmentId, linkUrl = linkUrl
    )

    private fun BackupTagDto.toEntity() = TagEntity(
        id = id, name = name, normalizedName = normalizedName, colorSeed = colorSeed, createdAtMs = createdAtMs
    )

    private fun BackupNoteTagDto.toEntity() = NoteTagCrossRefEntity(
        noteId = noteId, tagId = tagId, source = source, confidence = confidence,
        isDismissed = isDismissed, updatedAtMs = updatedAtMs
    )

    private fun BackupAttachmentDto.toEntity(thumbPath: String?) = AttachmentEntity(
        id = id, noteId = noteId, relativeFilePath = relativeFilePath,
        thumbnailRelativePath = thumbPath, mimeType = mimeType,
        originalDisplayName = originalDisplayName, byteSize = byteSize,
        widthPx = widthPx, heightPx = heightPx, sha256 = sha256,
        isScreenshot = isScreenshot, createdAtMs = createdAtMs
    )

    private fun BackupOcrDto.toEntity() = OcrResultEntity(
        attachmentId = attachmentId, recognizedText = recognizedText,
        languageCode = languageCode, avgConfidence = avgConfidence,
        status = status, processedAtMs = processedAtMs, engineName = engineName
    )

    private fun BackupLinkDto.toEntity() = LinkPreviewEntity(
        id = id, noteId = noteId, rawUrl = rawUrl, canonicalUrl = canonicalUrl,
        domain = domain, pageTitle = pageTitle, description = description,
        fetchStatus = fetchStatus, updatedAtMs = updatedAtMs
    )

    private fun BackupReminderDto.toEntity() = ReminderEntity(
        id = id, noteId = noteId, triggerAtMs = triggerAtMs, zoneId = zoneId,
        localDateTimeIso = localDateTimeIso, repeatRule = repeatRule,
        isCompleted = isCompleted, completedAtMs = completedAtMs,
        snoozeCount = snoozeCount, useLiveUpdate = useLiveUpdate
    )
}
