package com.mdiwebma.diffviewer.box

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne

@Entity
data class DiffEntity(
    @Id var id: Long = 0,
    var historyId: Long = 0,
    var title: String = "",
    var originalName: String = "Original",
    var modifiedName: String = "Modified",
    var originalText: String = "",
    var modifiedText: String = "",
    var syntaxHighlighterKey: String? = null,
    var textNormalizerKey: String? = null,
    var status: Int = 0,
    var favoriteTime: Long = 0L,
    var createdTime: Long = System.currentTimeMillis(),
    var updatedTime: Long = System.currentTimeMillis(),
) {
    lateinit var history: ToOne<DiffGroupEntity>
}