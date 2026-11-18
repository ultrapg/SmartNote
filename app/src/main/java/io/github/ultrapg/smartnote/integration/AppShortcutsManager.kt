package io.github.ultrapg.smartnote.integration

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.ultrapg.smartnote.MainActivity
import io.github.ultrapg.smartnote.R
import io.github.ultrapg.smartnote.domain.model.NoteSummary

class AppShortcutsManager(
    private val context: Context
) {
    fun updateDynamicPinnedShortcuts(pinnedNotes: List<NoteSummary>) {
        runCatching {
            val dynamic = pinnedNotes
                .filter { it.isPinned && !it.isDeleted && !it.isArchived }
                .take(2)
                .map { note ->
                    val label = note.title.ifBlank { note.previewText.ifBlank { "Pinned note" } }.take(24)
                    val intent = Intent(context, MainActivity::class.java).apply {
                        action = MainActivity.ACTION_OPEN_NOTE
                        putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
                    }
                    ShortcutInfoCompat.Builder(context, "pinned_${note.id}")
                        .setShortLabel(label)
                        .setLongLabel("Open $label")
                        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_notification_note))
                        .setIntent(intent)
                        .build()
                }
            ShortcutManagerCompat.setDynamicShortcuts(context, dynamic)
        }
    }
}
