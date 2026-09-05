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
    var loadingStatus: Int = 0,        // 0: 로딩 전/미완료, 1: 로드 성공/완료, 2: 로드 실패 (download source from Github)
    var reviewStatus: Int = 0,   //0:Open,  1:Reviewing, 2:Pending, 3:Commented, 4:Approved, 5:Closed, 6:Merged
    var favoriteTime: Long = 0L,
    var createdTime: Long = System.currentTimeMillis(),
    var updatedTime: Long = System.currentTimeMillis(),
) {
    lateinit var history: ToOne<DiffGroupEntity>
}