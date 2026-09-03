package com.mdiwebma.diffviewer.box

import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique

@Entity
data class SettingEntry(
    @Id var id: Long = 0,

    @Unique(onConflict = ConflictStrategy.REPLACE) val key: String,

    val value: String,
)