package io.github.ultrapg.smartnote.data.repository

import io.github.ultrapg.smartnote.ai.SmartTitleGenerator
import io.github.ultrapg.smartnote.data.local.AttachmentEntity
import io.github.ultrapg.smartnote.data.local.AttachmentWithOcrRow
import io.github.ultrapg.smartnote.data.local.LinkPreviewEntity
import io.github.ultrapg.smartnote.data.local.NoteBlockEntity
import io.github.ultrapg.smartnote.data.local.NoteEntity
import io.github.ultrapg.smartnote.data.local.NoteTagWithInfo
import io.github.ultrapg.smartnote.data.local.OcrResultEntity
import io.github.ultrapg.smartnote.data.local.ReminderEntity
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.domain.model.AttachmentInfo
import io.github.ultrapg.smartnote.domain.model.ChecklistPreviewItem
import io.github.ultrapg.smartnote.domain.model.LinkFetchStatus
import io.github.ultrapg.smartnote.domain.model.LinkPreviewInfo
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.NoteDetail
import io.github.ultrapg.smartnote.domain.model.NoteFilterType
import io.github.ultrapg.smartnote.domain.model.NoteSortOrder
import io.github.ultrapg.smartnote.domain.model.NoteSourceType
import io.github.ultrapg.smartnote.domain.model.NoteSummary
import io.github.ultrapg.smartnote.domain.model.NoteTag
import io.github.ultrapg.smartnote.domain.model.OcrStatus
import io.github.ultrapg.smartnote.domain.model.ReminderInfo
import io.github.ultrapg.smartnote.domain.model.RepeatRule
import io.github.ultrapg.smartnote.domain.model.StorageStats
import io.github.ultrapg.smartnote.domain.model.TagSource
import io.github.ultrapg.smartnote.domain.util.TextHashing
import io.github.ultrapg.smartnote.links.SmartLinkDetector
import io.github.ultrapg.smartnote.search.SearchIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.util.UUID

class NoteRepository(
    private val database: SmartNoteDatabase,
    private val attachmentStorage: AttachmentStorage,
    private val searchIndexer: SearchIndexer,
    private val tagRepository: TagRepository,
    private val onNoteChangedForBackground: (String) -> Unit = {},
    private val onNotesUiMutated: () -> Unit = {},
    private val onReminderChanged: (String) -> Unit = {}
) {
    private val noteDao = database.noteDao()
    private val blockDao = database.noteBlockDao()
    private val tagDao = database.tagDao()
    private val attachmentDao = database.attachmentDao()
    private val linkDao = database.linkPreviewDao()
    private val reminderDao = database.reminderDao()

    private val sharedCatalogFlow: Flow<CatalogSnapshot> = combine(
        blockDao.observeAllBlocks(),
        tagDao.observeAllActiveNoteTags(),
        attachmentDao.observeAllAttachmentsWithOcr(),
        linkDao.observeAllLinkPreviews(),
        reminderDao.observeAllReminders()
    ) { blocks, tags, attachments, links, reminders ->
        CatalogSnapshot(
            blocksByNote = blocks.groupBy { it.noteId },
            tagsByNote = tags.groupBy { it.noteId },
            attachmentsByNote = attachments.groupBy { it.noteId },
            linksByNote = links.groupBy { it.noteId },
            remindersByNote = reminders.associateBy { it.noteId }
        )
    }

    fun observeActiveNotes(
        sortOrder: NoteSortOrder = NoteSortOrder.UPDATED_DESC,
        filterType: NoteFilterType = NoteFilterType.ALL,
        tagId: String? = null
    ): Flow<List<NoteSummary>> = combine(
        noteDao.observeActiveNotes(),
        sharedCatalogFlow
    ) { notes, catalog ->
        notes
            .map { entity -> mapToSummary(entity, catalog) }
            .filter { summary ->
                val matchesTag = tagId == null || summary.tags.any { it.tagId == tagId }
                val matchesFilter = when (filterType) {
                    NoteFilterType.ALL -> true
                    NoteFilterType.PINNED -> summary.isPinned
                    NoteFilterType.REMINDERS -> summary.reminder != null && !summary.reminder.isCompleted
                    NoteFilterType.IMAGES -> summary.imageCount > 0
                    NoteFilterType.CHECKLISTS -> summary.hasChecklist
                    NoteFilterType.LINKS -> summary.hasLink
                }
                matchesTag && matchesFilter
            }
            .let { filtered ->
                if (filterType == NoteFilterType.REMINDERS) {
                    filtered.sortedWith(
                        compareBy<NoteSummary> { it.reminder?.triggerAtMs ?: Long.MAX_VALUE }
                            .thenByDescending { it.updatedAtMs }
                    )
                } else {
                    filtered.sortSummaries(sortOrder)
                }
            }
    }.flowOn(Dispatchers.Default)

    fun observeArchivedNotes(): Flow<List<NoteSummary>> = combine(
        noteDao.observeArchivedNotes(),
        sharedCatalogFlow
    ) { notes, catalog ->
        notes.map { mapToSummary(it, catalog) }
    }.flowOn(Dispatchers.Default)

    fun observeDeletedNotes(): Flow<List<NoteSummary>> = combine(
        noteDao.observeDeletedNotes(),
        sharedCatalogFlow
    ) { notes, catalog ->
        notes.map { mapToSummary(it, catalog) }
    }.flowOn(Dispatchers.Default)

    fun observeNoteDetail(noteId: String): Flow<NoteDetail?> = combine(
        noteDao.observeNoteById(noteId),
        blockDao.observeBlocksForNote(noteId),
        tagDao.observeTagsForNote(noteId),
        attachmentDao.observeAttachmentsForNote(noteId),
        linkDao.observeLinksForNote(noteId),
        reminderDao.observeReminderForNote(noteId)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val note = values[0] as? NoteEntity ?: return@combine null
        @Suppress("UNCHECKED_CAST")
        val blocks = values[1] as List<NoteBlockEntity>
        @Suppress("UNCHECKED_CAST")
        val tags = values[2] as List<NoteTagWithInfo>
        @Suppress("UNCHECKED_CAST")
        val attachments = values[3] as List<AttachmentWithOcrRow>
        @Suppress("UNCHECKED_CAST")
        val links = values[4] as List<LinkPreviewEntity>
        val reminder = values[5] as? ReminderEntity

        buildNoteDetail(note, blocks, tags, attachments, links, reminder)
    }.flowOn(Dispatchers.Default)

    suspend fun getNoteDetail(noteId: String): NoteDetail? {
        val note = noteDao.getNoteById(noteId) ?: return null
        return buildNoteDetail(
            note = note,
            blocks = blockDao.getBlocksForNote(noteId),
            tags = tagDao.getActiveTagsForNote(noteId),
            attachments = attachmentDao.getAttachmentsForNote(noteId),
            links = linkDao.getLinksForNote(noteId),
            reminder = reminderDao.getReminderForNote(noteId)
        )
    }

    suspend fun getAllSummariesSnapshot(includeArchived: Boolean = false): List<NoteSummary> {
        val notes = if (includeArchived) {
            noteDao.getAllNonDeletedNotes()
        } else {
            noteDao.getAllActiveNotes()
        }
        val catalog = CatalogSnapshot(
            blocksByNote = blockDao.getAllBlocksSnapshot().groupBy { it.noteId },
            tagsByNote = tagDao.getAllCrossRefsSnapshot().filter { !it.isDismissed }.let { refs ->
                val tagsMap = tagDao.getAllTagsSnapshot().associateBy { it.id }
                refs.mapNotNull { ref ->
                    val tag = tagsMap[ref.tagId] ?: return@mapNotNull null
                    NoteTagWithInfo(
                        noteId = ref.noteId,
                        tagId = ref.tagId,
                        tagName = tag.name,
                        source = ref.source,
                        confidence = ref.confidence,
                        isDismissed = ref.isDismissed,
                        colorSeed = tag.colorSeed
                    )
                }.groupBy { it.noteId }
            },
            attachmentsByNote = attachmentDao.getAllAttachmentsSnapshot().let { atts ->
                val ocrs = attachmentDao.getAllOcrResultsSnapshot().associateBy { it.attachmentId }
                atts.map { a ->
                    val ocr = ocrs[a.id]
                    AttachmentWithOcrRow(
                        id = a.id,
                        noteId = a.noteId,
                        relativeFilePath = a.relativeFilePath,
                        thumbnailRelativePath = a.thumbnailRelativePath,
                        mimeType = a.mimeType,
                        originalDisplayName = a.originalDisplayName,
                        byteSize = a.byteSize,
                        widthPx = a.widthPx,
                        heightPx = a.heightPx,
                        sha256 = a.sha256,
                        isScreenshot = a.isScreenshot,
                        createdAtMs = a.createdAtMs,
                        recognizedText = ocr?.recognizedText,
                        ocrStatus = ocr?.status
                    )
                }.groupBy { it.noteId }
            },
            linksByNote = linkDao.getAllLinksSnapshot().groupBy { it.noteId },
            remindersByNote = reminderDao.getAllRemindersSnapshot().associateBy { it.noteId }
        )
        return notes.map { mapToSummary(it, catalog) }
    }

    suspend fun createNote(
        id: String = UUID.randomUUID().toString(),
        title: String = "",
        blocks: List<NoteBlock> = emptyList(),
        initialTags: List<String> = emptyList(),
        isPinned: Boolean = false,
        sourceType: NoteSourceType = NoteSourceType.MANUAL,
        attachments: List<AttachmentEntity> = emptyList(),
        scheduleBackgroundWork: Boolean = true
    ): String {
        val now = System.currentTimeMillis()
        val normalizedBlocks = if (blocks.isEmpty()) {
            listOf(
                NoteBlock(
                    id = UUID.randomUUID().toString(),
                    noteId = id,
                    position = 0,
                    type = NoteBlockType.TEXT,
                    textContent = ""
                )
            )
        } else {
            blocks.mapIndexed { index, block ->
                block.copy(noteId = id, position = index)
            }
        }

        upsertNoteInternal(
            noteId = id,
            title = title,
            blocks = normalizedBlocks,
            isPinned = isPinned,
            isArchived = false,
            sourceType = sourceType,
            createdAtMs = now,
            updatedAtMs = now,
            newRevision = 1L,
            attachments = attachments
        )

        for (tagName in initialTags) {
            tagRepository.addUserTagToNote(id, tagName)
        }

        searchIndexer.indexNote(id)
        onNotesUiMutated()
        if (scheduleBackgroundWork) {
            onNoteChangedForBackground(id)
        }
        return id
    }

    suspend fun saveNoteContent(
        noteId: String,
        title: String,
        blocks: List<NoteBlock>,
        isPinned: Boolean? = null,
        scheduleBackgroundWork: Boolean = true,
        defaultSourceType: NoteSourceType = NoteSourceType.MANUAL
    ) {
        val existing = noteDao.getNoteById(noteId)
        val now = System.currentTimeMillis()
        val activeAttachmentIds = attachmentDao.getAttachmentsForNote(noteId).map { it.id }
        val referencedAttachmentIds = blocks
            .filter { it.type == NoteBlockType.IMAGE }
            .mapNotNull { it.attachmentId }
            .toHashSet()
        val preservedImageBlocks = activeAttachmentIds
            .filterNot { it in referencedAttachmentIds }
            .map { attId ->
                NoteBlock(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    position = blocks.size,
                    type = NoteBlockType.IMAGE,
                    attachmentId = attId
                )
            }
        val ordered = (blocks + preservedImageBlocks).mapIndexed { idx, b ->
            b.copy(noteId = noteId, position = idx)
        }
        val resolvedPinned = isPinned ?: existing?.isPinned ?: false
        val createdAt = existing?.createdAtMs ?: now
        val nextRev = (existing?.contentRevision ?: 0L) + 1L
        val sourceType = enumOrDefault(existing?.sourceType, defaultSourceType)

        upsertNoteInternal(
            noteId = noteId,
            title = title,
            blocks = ordered,
            isPinned = resolvedPinned,
            isArchived = existing?.isArchived ?: false,
            sourceType = sourceType,
            createdAtMs = createdAt,
            updatedAtMs = now,
            newRevision = nextRev,
            attachments = emptyList()
        )
        searchIndexer.indexNote(noteId)
        onNotesUiMutated()
        if (scheduleBackgroundWork) {
            onNoteChangedForBackground(noteId)
        }
    }

    /**
     * Appends a quick line (e.g. from a bridged Wear OS notification reply) to an existing note.
     */
    suspend fun appendQuickLineToNote(noteId: String, text: String): Boolean {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return false
        val detail = getNoteDetail(noteId) ?: return false
        if (detail.isDeleted) return false

        val existingBlocks = detail.blocks
        val lastBlock = existingBlocks.lastOrNull()
        val updatedBlocks = if (lastBlock != null && lastBlock.type == NoteBlockType.TEXT) {
            val newText = if (lastBlock.textContent.isBlank()) {
                cleaned
            } else {
                "${lastBlock.textContent.trimEnd()}\n• $cleaned"
            }
            existingBlocks.dropLast(1) + lastBlock.copy(textContent = newText)
        } else {
            existingBlocks + NoteBlock(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                position = existingBlocks.size,
                type = NoteBlockType.TEXT,
                textContent = cleaned
            )
        }

        saveNoteContent(
            noteId = noteId,
            title = detail.title,
            blocks = updatedBlocks,
            isPinned = detail.isPinned,
            scheduleBackgroundWork = false
        )
        return true
    }

    /**
     * Atomically applies an offline-generated title if and only if the note has not moved
     * past [expectedRevision] and still has a blank or generic placeholder title.
     */
    suspend fun applyGeneratedTitleIfUnmodified(
        noteId: String,
        expectedRevision: Long,
        generatedTitle: String
    ): Boolean {
        val cleaned = SmartTitleGenerator.formatTitle(generatedTitle)
        if (cleaned.isBlank()) return false

        val applied = database.inWriteTransaction {
            val existing = noteDao.getNoteById(noteId) ?: return@inWriteTransaction false
            if (existing.isDeleted || existing.contentRevision != expectedRevision) {
                return@inWriteTransaction false
            }
            if (!SmartTitleGenerator.isPlaceholderOrBlankTitle(existing.title)) {
                return@inWriteTransaction false
            }
            val normalizedForHash = TextHashing.normalizeForHash(cleaned, existing.fullPlainText)
            val sha256 = if (normalizedForHash.isBlank()) "" else TextHashing.sha256Hex(normalizedForHash)
            val simHash = TextHashing.computeSimHash64(normalizedForHash)
            val wordCount = TextHashing.tokenizeWords("$cleaned ${existing.fullPlainText}").size

            noteDao.upsertNote(
                existing.copy(
                    title = cleaned,
                    wordCount = wordCount,
                    contentSha256 = sha256,
                    simhash64 = simHash
                )
            )
            true
        }
        if (applied) {
            onNotesUiMutated()
        }
        return applied
    }

    private suspend fun upsertNoteInternal(
        noteId: String,
        title: String,
        blocks: List<NoteBlock>,
        isPinned: Boolean,
        isArchived: Boolean,
        sourceType: NoteSourceType,
        createdAtMs: Long,
        updatedAtMs: Long,
        newRevision: Long,
        attachments: List<AttachmentEntity>
    ) {
        val plainLines = blocks.mapNotNull { block ->
            when (block.type) {
                NoteBlockType.TEXT -> block.textContent.takeIf { it.isNotBlank() }
                NoteBlockType.CHECKLIST -> {
                    val mark = if (block.isChecked) "[x] " else "[ ] "
                    "$mark${block.textContent}".takeIf { block.textContent.isNotBlank() }
                }
                NoteBlockType.LINK -> block.linkUrl?.takeIf { it.isNotBlank() }
                NoteBlockType.IMAGE -> null
            }
        }
        val fullPlainText = plainLines.joinToString("\n")
        val preview = fullPlainText.take(500)
        val wordCount = TextHashing.tokenizeWords("$title $fullPlainText").size
        val checklistBlocks = blocks.filter { it.type == NoteBlockType.CHECKLIST }
        val checkedCount = checklistBlocks.count { it.isChecked }
        val normalizedForHash = TextHashing.normalizeForHash(title, fullPlainText)
        val sha256 = if (normalizedForHash.isBlank()) "" else TextHashing.sha256Hex(normalizedForHash)
        val simHash = TextHashing.computeSimHash64(normalizedForHash)

        val primaryAttachmentId = blocks.firstOrNull { it.type == NoteBlockType.IMAGE }?.attachmentId
            ?: attachments.firstOrNull()?.id

        val detectedLinks = buildList {
            blocks.forEach { block ->
                when (block.type) {
                    NoteBlockType.TEXT, NoteBlockType.CHECKLIST -> addAll(SmartLinkDetector.extractLinks(block.textContent))
                    NoteBlockType.LINK -> block.linkUrl?.let { SmartLinkDetector.parseSingleUrl(it)?.let(::add) }
                    NoteBlockType.IMAGE -> Unit
                }
            }
        }.distinctBy { it.canonicalUrl }

        val existingLinksByCanonical = linkDao.getLinksForNote(noteId).associateBy { it.canonicalUrl }

        val noteEntity = NoteEntity(
            id = noteId,
            title = title.trimEnd(),
            plainTextPreview = preview,
            fullPlainText = fullPlainText,
            wordCount = wordCount,
            isPinned = isPinned,
            isArchived = isArchived,
            isDeleted = false,
            deletedAtMs = null,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            contentRevision = newRevision,
            hasChecklist = checklistBlocks.isNotEmpty(),
            checklistCheckedCount = checkedCount,
            checklistTotalCount = checklistBlocks.size,
            primaryAttachmentId = primaryAttachmentId,
            contentSha256 = sha256,
            simhash64 = simHash,
            sourceType = sourceType.name
        )

        val blockEntities = blocks.mapIndexed { index, b ->
            NoteBlockEntity(
                id = b.id,
                noteId = noteId,
                position = index,
                blockType = b.type.name,
                textContent = b.textContent,
                isChecked = b.isChecked,
                indentLevel = b.indentLevel,
                attachmentId = b.attachmentId,
                linkUrl = b.linkUrl
            )
        }

        val linkEntities = detectedLinks.map { link ->
            val prev = existingLinksByCanonical[link.canonicalUrl]
            prev ?: LinkPreviewEntity(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                rawUrl = link.rawUrl,
                canonicalUrl = link.canonicalUrl,
                domain = link.domain,
                fetchStatus = LinkFetchStatus.LOCAL_ONLY.name,
                updatedAtMs = updatedAtMs
            )
        }

        val normalizedAttachments = attachments.map { it.copy(noteId = noteId) }
        database.inWriteTransaction {
            noteDao.upsertNote(noteEntity)
            blockDao.replaceBlocksForNote(noteId, blockEntities)
            if (normalizedAttachments.isNotEmpty()) {
                attachmentDao.upsertAttachments(normalizedAttachments)
                attachmentDao.upsertOcrResults(
                    normalizedAttachments.map { a ->
                        OcrResultEntity(
                            attachmentId = a.id,
                            recognizedText = "",
                            status = OcrStatus.PENDING.name,
                            processedAtMs = updatedAtMs
                        )
                    }
                )
            }
            linkDao.deleteLinksForNote(noteId)
            if (linkEntities.isNotEmpty()) {
                linkDao.upsertLinks(linkEntities)
            }
        }
    }

    suspend fun addAttachmentToNote(
        noteId: String,
        attachment: AttachmentEntity,
        insertImageBlock: Boolean = true
    ) {
        val normalizedAttachment = attachment.copy(noteId = noteId)
        val now = System.currentTimeMillis()
        database.inWriteTransaction {
            val existing = noteDao.getNoteById(noteId)
            if (existing == null) {
                noteDao.upsertNote(
                    NoteEntity(
                        id = noteId,
                        title = "",
                        plainTextPreview = "",
                        fullPlainText = "",
                        wordCount = 0,
                        isPinned = false,
                        isArchived = false,
                        isDeleted = false,
                        deletedAtMs = null,
                        createdAtMs = now,
                        updatedAtMs = now,
                        contentRevision = 1L,
                        primaryAttachmentId = normalizedAttachment.id
                    )
                )
            } else {
                noteDao.upsertNote(
                    existing.copy(
                        isDeleted = false,
                        deletedAtMs = null,
                        primaryAttachmentId = existing.primaryAttachmentId ?: normalizedAttachment.id,
                        updatedAtMs = now,
                        contentRevision = existing.contentRevision + 1L
                    )
                )
            }
            attachmentDao.upsertAttachment(normalizedAttachment)
            attachmentDao.upsertOcrResult(
                OcrResultEntity(
                    attachmentId = normalizedAttachment.id,
                    recognizedText = "",
                    status = OcrStatus.PENDING.name,
                    processedAtMs = now
                )
            )
            if (insertImageBlock) {
                val existingBlocks = blockDao.getBlocksForNote(noteId)
                val newBlock = NoteBlockEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    position = existingBlocks.size,
                    blockType = NoteBlockType.IMAGE.name,
                    attachmentId = normalizedAttachment.id
                )
                blockDao.insertBlocks(listOf(newBlock))
            }
        }
        searchIndexer.indexNote(noteId)
        onNotesUiMutated()
        onNoteChangedForBackground(noteId)
    }

    suspend fun removeAttachmentBlock(noteId: String, blockId: String, attachmentId: String?) {
        val detail = getNoteDetail(noteId) ?: return
        if (attachmentId != null) {
            val att = attachmentDao.getAttachmentById(attachmentId)
            attachmentDao.deleteAttachment(attachmentId)
            if (att != null && attachmentDao.countOtherReferencesToPath(att.relativeFilePath, noteId) == 0) {
                attachmentStorage.deleteRelativeFileQuietly(att.relativeFilePath)
                attachmentStorage.deleteRelativeFileQuietly(att.thumbnailRelativePath)
            }
        }
        val remainingBlocks = detail.blocks.filterNot {
            it.id == blockId || (attachmentId != null && it.attachmentId == attachmentId)
        }
        saveNoteContent(noteId, detail.title, remainingBlocks, scheduleBackgroundWork = false)
        val stillHasPrimary = remainingBlocks.firstOrNull { it.type == NoteBlockType.IMAGE }?.attachmentId
        noteDao.getNoteById(noteId)?.let { note ->
            if (note.primaryAttachmentId != stillHasPrimary) {
                noteDao.upsertNote(note.copy(primaryAttachmentId = stillHasPrimary))
            }
        }
        searchIndexer.indexNote(noteId)
        onNotesUiMutated()
    }

    suspend fun toggleChecklistBlock(noteId: String, blockId: String) {
        val detail = getNoteDetail(noteId) ?: return
        val updatedBlocks = detail.blocks.map { block ->
            if (block.id == blockId && block.type == NoteBlockType.CHECKLIST) {
                block.copy(isChecked = !block.isChecked)
            } else {
                block
            }
        }
        saveNoteContent(noteId, detail.title, updatedBlocks, scheduleBackgroundWork = false)
    }

    suspend fun setPinned(noteId: String, pinned: Boolean) {
        noteDao.setPinned(noteId, pinned, System.currentTimeMillis())
        onNotesUiMutated()
    }

    suspend fun setArchived(noteId: String, archived: Boolean) {
        noteDao.setArchived(noteId, archived, System.currentTimeMillis())
        onNotesUiMutated()
    }

    suspend fun duplicateNote(noteId: String): String? {
        val original = getNoteDetail(noteId) ?: return null
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val attachmentIdMap = mutableMapOf<String, String>()
        val duplicatedAttachments = original.attachments.map { att ->
            val newAttId = UUID.randomUUID().toString()
            attachmentIdMap[att.id] = newAttId
            AttachmentEntity(
                id = newAttId,
                noteId = newId,
                relativeFilePath = att.relativeFilePath,
                thumbnailRelativePath = att.thumbnailRelativePath,
                mimeType = att.mimeType,
                originalDisplayName = att.originalDisplayName,
                byteSize = att.byteSize,
                widthPx = att.widthPx,
                heightPx = att.heightPx,
                sha256 = att.sha256,
                isScreenshot = att.isScreenshot,
                createdAtMs = now
            )
        }
        val duplicatedBlocks = original.blocks.mapIndexed { idx, b ->
            b.copy(
                id = UUID.randomUUID().toString(),
                noteId = newId,
                position = idx,
                attachmentId = b.attachmentId?.let { attachmentIdMap[it] ?: it }
            )
        }
        val newTitle = if (original.title.isBlank()) "Copy" else "${original.title} (Copy)"
        createNote(
            id = newId,
            title = newTitle,
            blocks = duplicatedBlocks,
            initialTags = original.tags.filter { it.source == TagSource.USER }.map { it.name },
            isPinned = false,
            sourceType = original.sourceType,
            attachments = duplicatedAttachments,
            scheduleBackgroundWork = false
        )
        // Copy cached OCR text immediately for duplicated attachments
        original.attachments.forEach { srcAtt ->
            val newAttId = attachmentIdMap[srcAtt.id] ?: return@forEach
            val ocrText = srcAtt.ocrText
            if (!ocrText.isNullOrBlank()) {
                attachmentDao.upsertOcrResult(
                    OcrResultEntity(
                        attachmentId = newAttId,
                        recognizedText = ocrText,
                        status = OcrStatus.COMPLETED.name,
                        processedAtMs = now
                    )
                )
            }
        }
        searchIndexer.indexNote(newId)
        onNotesUiMutated()
        return newId
    }

    suspend fun moveToRecycleBin(noteId: String) {
        noteDao.moveToRecycleBin(noteId, System.currentTimeMillis())
        searchIndexer.removeNote(noteId)
        onReminderChanged(noteId)
        onNotesUiMutated()
    }

    suspend fun restoreFromRecycleBin(noteId: String) {
        noteDao.restoreFromRecycleBin(noteId, System.currentTimeMillis())
        searchIndexer.indexNote(noteId)
        onReminderChanged(noteId)
        onNotesUiMutated()
    }

    suspend fun deletePermanently(noteId: String) {
        val attachments = attachmentDao.getAttachmentsForNote(noteId)
        val links = linkDao.getLinksForNote(noteId)
        noteDao.deleteNotePermanently(noteId)
        searchIndexer.removeNote(noteId)
        attachments.forEach { att ->
            if (attachmentDao.countOtherReferencesToPath(att.relativeFilePath, noteId) == 0) {
                attachmentStorage.deleteRelativeFileQuietly(att.relativeFilePath)
                attachmentStorage.deleteRelativeFileQuietly(att.thumbnailRelativePath)
            }
        }
        links.forEach { link ->
            attachmentStorage.deleteRelativeFileQuietly(link.previewImagePath)
        }
        onReminderChanged(noteId)
        onNotesUiMutated()
    }

    suspend fun emptyRecycleBin() {
        val deleted = noteDao.getAllNotesSnapshot().filter { it.isDeleted }
        deleted.forEach { deletePermanently(it.id) }
    }

    suspend fun purgeExpiredRecycleBin(retentionDays: Int = 30, nowMs: Long = System.currentTimeMillis()): Int {
        val cutoff = nowMs - retentionDays.toLong() * 24L * 60L * 60L * 1000L
        val expired = noteDao.getExpiredDeletedNotes(cutoff)
        expired.forEach { deletePermanently(it.id) }
        return expired.size
    }

    suspend fun isNoteCompletelyBlank(noteId: String): Boolean {
        val note = noteDao.getNoteById(noteId) ?: return false
        if (note.isDeleted || note.isArchived || note.isPinned ||
            note.title.isNotBlank() || note.fullPlainText.isNotBlank()
        ) {
            return false
        }
        val blocks = blockDao.getBlocksForNote(noteId)
        if (blocks.any { it.textContent.isNotBlank() || !it.attachmentId.isNullOrBlank() || !it.linkUrl.isNullOrBlank() }) {
            return false
        }
        if (attachmentDao.getAttachmentsForNote(noteId).isNotEmpty()) return false
        if (linkDao.getLinksForNote(noteId).isNotEmpty()) return false
        if (tagDao.getActiveTagsForNote(noteId).isNotEmpty()) return false
        if (reminderDao.getReminderForNote(noteId) != null) return false
        return true
    }

    suspend fun discardIfCompletelyBlank(noteId: String): Boolean {
        if (!isNoteCompletelyBlank(noteId)) return false
        deletePermanently(noteId)
        return true
    }

    suspend fun pruneBlankUntouchedNotes(olderThanOrEqualMs: Long = Long.MAX_VALUE): Int {
        val candidates = noteDao.getAllActiveNotes().filter { note ->
            !note.isPinned &&
                !note.isArchived &&
                !note.isDeleted &&
                note.title.isBlank() &&
                note.fullPlainText.isBlank() &&
                note.updatedAtMs <= olderThanOrEqualMs
        }
        var removedCount = 0
        for (candidate in candidates) {
            if (discardIfCompletelyBlank(candidate.id)) {
                removedCount++
            }
        }
        return removedCount
    }

    suspend fun getStorageStats(): StorageStats {
        val allAttachments = attachmentDao.getAllAttachmentsSnapshot()
        return StorageStats(
            noteCount = noteDao.countActiveNotes(),
            archivedCount = noteDao.countArchivedNotes(),
            recycleBinCount = noteDao.countDeletedNotes(),
            attachmentCount = allAttachments.size,
            attachmentsBytes = attachmentStorage.directorySizeBytes(attachmentStorage.attachmentsDir),
            thumbnailsBytes = attachmentStorage.directorySizeBytes(attachmentStorage.thumbnailsDir),
            databaseBytes = attachmentStorage.databaseSizeBytes()
        )
    }

    private fun mapToSummary(entity: NoteEntity, catalog: CatalogSnapshot): NoteSummary {
        val blocks = catalog.blocksByNote[entity.id].orEmpty()
        val tags = catalog.tagsByNote[entity.id].orEmpty().map { t ->
            NoteTag(
                tagId = t.tagId,
                name = t.tagName,
                source = enumOrDefault(t.source, TagSource.USER),
                confidence = t.confidence,
                colorSeed = t.colorSeed
            )
        }
        val attachments = catalog.attachmentsByNote[entity.id].orEmpty()
        val primaryAtt = attachments.firstOrNull { it.id == entity.primaryAttachmentId }
            ?: attachments.firstOrNull()
        val thumbPath = primaryAtt?.thumbnailRelativePath ?: primaryAtt?.relativeFilePath
        val links = catalog.linksByNote[entity.id].orEmpty()
        val reminder = catalog.remindersByNote[entity.id]?.let(::mapReminder)
        val checklistItems = blocks
            .filter { it.blockType == NoteBlockType.CHECKLIST.name && it.textContent.isNotBlank() }
            .take(5)
            .map { ChecklistPreviewItem(id = it.id, text = it.textContent, isChecked = it.isChecked) }

        return NoteSummary(
            id = entity.id,
            title = entity.title,
            previewText = entity.plainTextPreview,
            wordCount = entity.wordCount,
            isPinned = entity.isPinned,
            isArchived = entity.isArchived,
            isDeleted = entity.isDeleted,
            deletedAtMs = entity.deletedAtMs,
            createdAtMs = entity.createdAtMs,
            updatedAtMs = entity.updatedAtMs,
            hasChecklist = entity.hasChecklist,
            checklistCheckedCount = entity.checklistCheckedCount,
            checklistTotalCount = entity.checklistTotalCount,
            checklistPreview = checklistItems,
            primaryThumbnailPath = thumbPath,
            imageCount = attachments.size,
            tags = tags,
            reminder = reminder,
            hasLink = links.isNotEmpty(),
            primaryLinkDomain = links.firstOrNull()?.domain,
            ocrSnippet = attachments.firstNotNullOfOrNull { it.recognizedText?.takeIf(String::isNotBlank) },
            sourceType = enumOrDefault(entity.sourceType, NoteSourceType.MANUAL)
        )
    }

    private fun buildNoteDetail(
        note: NoteEntity,
        blocks: List<NoteBlockEntity>,
        tags: List<NoteTagWithInfo>,
        attachments: List<AttachmentWithOcrRow>,
        links: List<LinkPreviewEntity>,
        reminder: ReminderEntity?
    ): NoteDetail {
        return NoteDetail(
            id = note.id,
            title = note.title,
            isPinned = note.isPinned,
            isArchived = note.isArchived,
            isDeleted = note.isDeleted,
            deletedAtMs = note.deletedAtMs,
            createdAtMs = note.createdAtMs,
            updatedAtMs = note.updatedAtMs,
            contentRevision = note.contentRevision,
            sourceType = enumOrDefault(note.sourceType, NoteSourceType.MANUAL),
            blocks = blocks.map { b ->
                NoteBlock(
                    id = b.id,
                    noteId = b.noteId,
                    position = b.position,
                    type = enumOrDefault(b.blockType, NoteBlockType.TEXT),
                    textContent = b.textContent,
                    isChecked = b.isChecked,
                    indentLevel = b.indentLevel,
                    attachmentId = b.attachmentId,
                    linkUrl = b.linkUrl
                )
            },
            tags = tags.map { t ->
                NoteTag(
                    tagId = t.tagId,
                    name = t.tagName,
                    source = enumOrDefault(t.source, TagSource.USER),
                    confidence = t.confidence,
                    colorSeed = t.colorSeed
                )
            },
            attachments = attachments.map { a ->
                AttachmentInfo(
                    id = a.id,
                    noteId = a.noteId,
                    relativeFilePath = a.relativeFilePath,
                    thumbnailRelativePath = a.thumbnailRelativePath,
                    mimeType = a.mimeType,
                    originalDisplayName = a.originalDisplayName,
                    byteSize = a.byteSize,
                    widthPx = a.widthPx,
                    heightPx = a.heightPx,
                    sha256 = a.sha256,
                    isScreenshot = a.isScreenshot,
                    createdAtMs = a.createdAtMs,
                    ocrText = a.recognizedText,
                    ocrStatus = enumOrDefault(a.ocrStatus, OcrStatus.PENDING)
                )
            },
            links = links.map { l ->
                LinkPreviewInfo(
                    id = l.id,
                    noteId = l.noteId,
                    rawUrl = l.rawUrl,
                    canonicalUrl = l.canonicalUrl,
                    domain = l.domain,
                    pageTitle = l.pageTitle,
                    description = l.description,
                    previewImagePath = l.previewImagePath,
                    fetchStatus = enumOrDefault(l.fetchStatus, LinkFetchStatus.LOCAL_ONLY),
                    updatedAtMs = l.updatedAtMs
                )
            },
            reminder = reminder?.let(::mapReminder)
        )
    }

    private fun mapReminder(entity: ReminderEntity): ReminderInfo = ReminderInfo(
        id = entity.id,
        noteId = entity.noteId,
        triggerAtMs = entity.triggerAtMs,
        zoneId = entity.zoneId,
        localDateTimeIso = entity.localDateTimeIso,
        repeatRule = enumOrDefault(entity.repeatRule, RepeatRule.NONE),
        isCompleted = entity.isCompleted,
        completedAtMs = entity.completedAtMs,
        snoozeCount = entity.snoozeCount,
        useLiveUpdate = entity.useLiveUpdate,
        repeatDaysMask = entity.repeatDaysMask,
        repeatInterval = entity.repeatInterval.coerceAtLeast(1),
        preAlertMinutes = entity.preAlertMinutes.coerceAtLeast(0),
        isImportant = entity.isImportant
    )

    private fun List<NoteSummary>.sortSummaries(sortOrder: NoteSortOrder): List<NoteSummary> = when (sortOrder) {
        NoteSortOrder.UPDATED_DESC -> sortedWith(
            compareByDescending<NoteSummary> { it.isPinned }.thenByDescending { it.updatedAtMs }
        )
        NoteSortOrder.CREATED_DESC -> sortedWith(
            compareByDescending<NoteSummary> { it.isPinned }.thenByDescending { it.createdAtMs }
        )
        NoteSortOrder.TITLE_ASC -> sortedWith(
            compareByDescending<NoteSummary> { it.isPinned }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.ifBlank { it.previewText } }
        )
    }

    private data class CatalogSnapshot(
        val blocksByNote: Map<String, List<NoteBlockEntity>>,
        val tagsByNote: Map<String, List<NoteTagWithInfo>>,
        val attachmentsByNote: Map<String, List<AttachmentWithOcrRow>>,
        val linksByNote: Map<String, List<LinkPreviewEntity>>,
        val remindersByNote: Map<String, ReminderEntity>
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}
