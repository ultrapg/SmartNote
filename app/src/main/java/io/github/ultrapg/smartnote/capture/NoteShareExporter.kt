package io.github.ultrapg.smartnote.capture

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.NoteDetail

class NoteShareExporter(
    private val context: Context,
    private val attachmentStorage: AttachmentStorage
) {
    fun buildShareText(note: NoteDetail): String = buildString {
        if (note.title.isNotBlank()) {
            append(note.title.trim()).append("\n\n")
        }
        note.blocks.forEach { block ->
            when (block.type) {
                NoteBlockType.TEXT -> {
                    if (block.textContent.isNotBlank()) append(block.textContent.trim()).append("\n\n")
                }
                NoteBlockType.CHECKLIST -> {
                    val mark = if (block.isChecked) "[x]" else "[ ]"
                    append(mark).append(' ').append(block.textContent.trim()).append('\n')
                }
                NoteBlockType.LINK -> {
                    block.linkUrl?.let { append(it).append('\n') }
                }
                NoteBlockType.IMAGE -> Unit
            }
        }
        if (note.tags.isNotEmpty()) {
            append('\n').append(note.tags.joinToString(" ") { "#${it.name.replace(' ', '_')}" })
        }
    }.trim()

    fun shareNote(note: NoteDetail) {
        val text = buildShareText(note)
        val authority = "${context.packageName}.fileprovider"
        val imageUris = note.attachments.mapNotNull { att ->
            runCatching {
                val file = attachmentStorage.resolvePrivateFile(att.relativeFilePath)
                if (file.exists()) FileProvider.getUriForFile(context, authority, file) else null
            }.getOrNull()
        }

        val shareIntent = when {
            imageUris.isEmpty() -> {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, note.title)
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }
            imageUris.size == 1 -> {
                val uri = imageUris.first()
                Intent(Intent.ACTION_SEND).apply {
                    type = note.attachments.firstOrNull()?.mimeType ?: "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (note.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, note.title)
                    if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
                    clipData = ClipData.newRawUri(note.title.ifBlank { "Shared image" }, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            else -> {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
                    if (note.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, note.title)
                    if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
                    val clip = ClipData.newRawUri(note.title.ifBlank { "Shared images" }, imageUris.first())
                    imageUris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }

        val chooser = Intent.createChooser(shareIntent, "Share note").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
