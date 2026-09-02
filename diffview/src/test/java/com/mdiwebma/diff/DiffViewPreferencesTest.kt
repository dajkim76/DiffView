package com.mdiwebma.diff

import android.content.SharedPreferences
import com.mdiwebma.diffview.DiffViewPreferences
import com.mdiwebma.diffview.model.DiffGranularity
import com.mdiwebma.diffview.model.DiffLongTabAction
import com.mdiwebma.diffview.model.DiffMode
import com.mdiwebma.diffview.model.DiffThemeMode
import com.mdiwebma.diffview.model.WhitespaceIgnoreMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DiffViewPreferencesTest {

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map

        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(this)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val sp: FakeSharedPreferences) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) temp[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) sp.map.clear()
                temp.forEach { (k, v) ->
                    if (v == null) sp.map.remove(k) else sp.map[k] = v
                }
            }
        }
    }

    @Test
    fun testPreferencesSaveAndLoad() {
        val sp = FakeSharedPreferences()
        val original = DiffViewPreferences(
            diffMode = DiffMode.UNIFIED,
            themeMode = DiffThemeMode.DARK,
            textSizeSp = 16f,
            isFoldingEnabled = false,
            contextLines = 5,
            foldingThreshold = 12,
            whitespaceIgnoreMode = WhitespaceIgnoreMode.IGNORE_ALL,
            diffGranularity = DiffGranularity.CHARACTER,
            isLineWrap = true,
            showDiffSymbols = false,
            longTabAction = DiffLongTabAction.COMMENT
        )

        original.saveTo(sp, keyPrefix = "test_")

        val loaded = DiffViewPreferences.loadFrom(sp, keyPrefix = "test_")

        assertEquals(DiffMode.UNIFIED, loaded.diffMode)
        assertEquals(DiffThemeMode.DARK, loaded.themeMode)
        assertEquals(16f, loaded.textSizeSp, 0.01f)
        assertEquals(false, loaded.isFoldingEnabled)
        assertEquals(5, loaded.contextLines)
        assertEquals(12, loaded.foldingThreshold)
        assertEquals(WhitespaceIgnoreMode.IGNORE_ALL, loaded.whitespaceIgnoreMode)
        assertEquals(DiffGranularity.CHARACTER, loaded.diffGranularity)
        assertEquals(true, loaded.isLineWrap)
        assertEquals(false, loaded.showDiffSymbols)
        assertEquals(DiffLongTabAction.COMMENT, loaded.longTabAction)
    }

    @Test
    fun testThemeModeDefault_isAuto() {
        val defaultPrefs = DiffViewPreferences()
        assertEquals(DiffThemeMode.AUTO, defaultPrefs.themeMode)
    }
}
