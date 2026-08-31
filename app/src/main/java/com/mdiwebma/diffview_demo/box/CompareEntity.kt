package com.mdiwebma.diffview_demo.box

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.converter.PropertyConverter

open class DefaultStringConverter(
    private val fallback: String = ""
) : PropertyConverter<String, String?> {

    override fun convertToEntityProperty(databaseValue: String?): String {
        return databaseValue?.ifEmpty { fallback } ?: fallback
    }

    override fun convertToDatabaseValue(entityProperty: String): String {
        return entityProperty.ifEmpty { fallback }
    }
}


class NonTitleConverter : DefaultStringConverter("No Title ^^")


class EmptyStringMigration : PropertyConverter<String, String?> {
    override fun convertToEntityProperty(databaseValue: String?): String {
        return databaseValue.orEmpty()
    }

    override fun convertToDatabaseValue(entityProperty: String): String {
        return entityProperty
    }
}

@Entity
data class CompareEntity(
    @Id var id: Long = 0,
    @Convert(converter = EmptyStringMigration::class, dbType = String::class)
    var title: String = "",
    @Convert(converter = EmptyStringMigration::class, dbType = String::class)
    var title2: String = "",
    var title3: String? = "",
    var beforeText: String = "",
    var afterText: String = "",
    var fileType: Int = 0, // default PLAIN TEXT
    var count: Int = 0,
    var isFavorite: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    @Index var updatedAt: Long = System.currentTimeMillis(),
)