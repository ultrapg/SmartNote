package io.github.ultrapg.smartnote.duplicates

import io.github.ultrapg.smartnote.data.local.DismissedDuplicateEntity
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.domain.model.DuplicatePairInfo
import io.github.ultrapg.smartnote.domain.model.NoteSummary
import io.github.ultrapg.smartnote.domain.util.TextHashing

class SmartDuplicateDetector(
    private val database: SmartNoteDatabase,
    private val noteRepository: NoteRepository
) {
    suspend fun findDuplicatesForAllNotes(): List<DuplicatePairInfo> {
        val activeEntities = database.noteDao().getAllActiveNotes()
        if (activeEntities.size < 2) return emptyList()

        val dismissed = database.searchAndMaintenanceDao().getDismissedDuplicateKeys().toHashSet()
        val summariesById = noteRepository.getAllSummariesSnapshot(includeArchived = false)
            .associateBy { it.id }

        val linksByNote = database.linkPreviewDao().getAllLinksSnapshot().groupBy { it.noteId }
        val attachmentsByNote = database.attachmentDao().getAllAttachmentsSnapshot().groupBy { it.noteId }

        val found = LinkedHashMap<String, DuplicatePairInfo>()

        fun recordPair(idA: String, idB: String, reason: String, score: Float) {
            if (idA == idB) return
            val key = canonicalPairKey(idA, idB)
            if (key in dismissed) return
            val existing = found[key]
            if (existing != null && existing.similarityScore >= score) return
            val summaryA = summariesById[idA] ?: return
            val summaryB = summariesById[idB] ?: return
            val ordered = if (summaryA.updatedAtMs >= summaryB.updatedAtMs) summaryA to summaryB else summaryB to summaryA
            found[key] = DuplicatePairInfo(
                pairKey = key,
                noteA = ordered.first,
                noteB = ordered.second,
                matchReason = reason,
                similarityScore = score
            )
        }

        // 1. Exact normalized text SHA-256
        activeEntities
            .filter { it.contentSha256.isNotBlank() && it.wordCount >= 3 }
            .groupBy { it.contentSha256 }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                for (i in 0 until group.size) {
                    for (j in i + 1 until group.size) {
                        recordPair(group[i].id, group[j].id, "Identical note text", 1.0f)
                    }
                }
            }

        // 2. Identical canonical URLs
        val notesByUrl = HashMap<String, MutableList<String>>()
        for ((noteId, links) in linksByNote) {
            if (noteId !in summariesById) continue
            for (link in links) {
                notesByUrl.getOrPut(link.canonicalUrl) { mutableListOf() }.add(noteId)
            }
        }
        notesByUrl.values.filter { it.size > 1 }.forEach { ids ->
            val distinctIds = ids.distinct()
            for (i in 0 until distinctIds.size) {
                for (j in i + 1 until distinctIds.size) {
                    recordPair(distinctIds[i], distinctIds[j], "Identical URL saved", 0.96f)
                }
            }
        }

        // 3. Identical imported image SHA-256
        val notesByImageSha = HashMap<String, MutableList<String>>()
        for ((noteId, atts) in attachmentsByNote) {
            if (noteId !in summariesById) continue
            for (att in atts) {
                if (att.sha256.isNotBlank()) {
                    notesByImageSha.getOrPut(att.sha256) { mutableListOf() }.add(noteId)
                }
            }
        }
        notesByImageSha.values.filter { it.size > 1 }.forEach { ids ->
            val distinctIds = ids.distinct()
            for (i in 0 until distinctIds.size) {
                for (j in i + 1 until distinctIds.size) {
                    recordPair(distinctIds[i], distinctIds[j], "Identical image attachment", 0.95f)
                }
            }
        }

        // 4. Similar text detection via 64-bit SimHash + Jaccard verification (notes >= 6 words)
        val substantial = activeEntities.filter { it.wordCount >= 6 && it.simhash64 != 0L }
        val tokenSets = HashMap<String, Set<String>>(substantial.size)
        for (i in 0 until substantial.size) {
            val a = substantial[i]
            for (j in i + 1 until substantial.size) {
                val b = substantial[j]
                val pairKey = canonicalPairKey(a.id, b.id)
                if (pairKey in dismissed || pairKey in found) continue
                val hamming = TextHashing.hammingDistance(a.simhash64, b.simhash64)
                if (hamming <= 10) {
                    val tokensA = tokenSets.getOrPut(a.id) {
                        TextHashing.tokenizeWords("${a.title} ${a.fullPlainText}").toSet()
                    }
                    val tokensB = tokenSets.getOrPut(b.id) {
                        TextHashing.tokenizeWords("${b.title} ${b.fullPlainText}").toSet()
                    }
                    val jaccard = TextHashing.jaccardSimilarity(tokensA, tokensB)
                    if (jaccard >= 0.72f) {
                        recordPair(a.id, b.id, "Highly similar text (${(jaccard * 100).toInt()}%)", jaccard)
                    }
                }
            }
        }

        return found.values.sortedByDescending { it.similarityScore }
    }

    suspend fun findDuplicatesForNote(noteId: String): List<DuplicatePairInfo> {
        return findDuplicatesForAllNotes().filter { it.noteA.id == noteId || it.noteB.id == noteId }
    }

    suspend fun dismissDuplicatePair(noteIdA: String, noteIdB: String) {
        val key = canonicalPairKey(noteIdA, noteIdB)
        database.searchAndMaintenanceDao().dismissDuplicatePair(
            DismissedDuplicateEntity(
                pairKey = key,
                dismissedAtMs = System.currentTimeMillis()
            )
        )
    }

    companion object {
        fun canonicalPairKey(idA: String, idB: String): String {
            return if (idA <= idB) "$idA::$idB" else "$idB::$idA"
        }
    }
}
