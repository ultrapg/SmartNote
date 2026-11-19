package io.github.ultrapg.smartnote.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object NotesOverviewRoute

@Serializable
data object SmartSearchRoute

@Serializable
data object TagsRoute

@Serializable
data class TagNotesRoute(
    val tagId: String,
    val tagName: String
)

@Serializable
data class NoteEditorRoute(
    val noteId: String? = null,
    val startChecklist: Boolean = false,
    val autoPickImage: Boolean = false,
    val sourceQuickTile: Boolean = false
)

@Serializable
data object ArchiveRoute

@Serializable
data object RecycleBinRoute

@Serializable
data object DuplicatesRoute

@Serializable
data object SettingsRoute

@Serializable
data object AppearanceSettingsRoute

@Serializable
data object SmartFeaturesSettingsRoute

@Serializable
data object AiSettingsRoute

@Serializable
data object IntegrationsSettingsRoute

@Serializable
data object DataSettingsRoute

@Serializable
data object AboutSettingsRoute
