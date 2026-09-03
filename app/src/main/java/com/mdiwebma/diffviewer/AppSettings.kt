package com.mdiwebma.diffviewer

import com.mdiwebma.diffviewer.box.AppBoxStore
import com.mdiwebma.diffviewer.box.SettingEntry
import com.mdiwebma.diffviewer.box.SettingEntry_
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object AppSettings {
    // Declare App settings
    val runCount = SettingLong("runCount", 0)


    //
    // Memory cache
    private val stringMap = ConcurrentHashMap<String, String>()
    private val longMap = ConcurrentHashMap<String, Long>()
    private val intMap = ConcurrentHashMap<String, Int>()
    private val booleanMap = ConcurrentHashMap<String, Boolean>()
    private val doubleMap = ConcurrentHashMap<String, Double>()

    private val dbExecutor = Executors.newSingleThreadExecutor()

    fun clearCache() {
        stringMap.clear()
        longMap.clear()
        intMap.clear()
        booleanMap.clear()
        doubleMap.clear()
    }

    //
    // Database operation
    private fun readStringFromDatabase(key: String): String? {
        val box = AppBoxStore.Companion.getInstance(MyApp.appContext).getBox<SettingEntry>()
        return box.query(SettingEntry_.key.equal(key)).build().use { query ->
            query.findFirst()?.value
        }
    }

    private fun writeStringToDatabase(key: String, value: String) {
        dbExecutor.execute {
            val appBoxStore = AppBoxStore.Companion.getInstance(MyApp.appContext)
            appBoxStore.boxStore.runInTx {
                val settingBox = appBoxStore.getBox<SettingEntry>()
                val entity = settingBox.query(SettingEntry_.key.equal(key)).build().use { query ->
                    query.findFirst()
                }
                if (entity != null) {
                    settingBox.put(entity.copy(value = value))
                } else {
                    settingBox.put(SettingEntry(key = key, value = value))
                }
            }
        }
    }

    fun removeDatabaseKey(key: String) {
        stringMap.remove(key)
        longMap.remove(key)
        intMap.remove(key)
        booleanMap.remove(key)
        doubleMap.remove(key)

        dbExecutor.execute {
            val appBoxStore = AppBoxStore.Companion.getInstance(MyApp.appContext)
            appBoxStore.getBox<SettingEntry>().query(SettingEntry_.key.equal(key)).build().use { query ->
                query.remove()
            }
        }
    }

    //
    // Helper method
    private fun getString(key: String, defaultValue: String): String {
        return stringMap.getOrPut(key) { readStringFromDatabase(key) ?: defaultValue }
    }

    private fun setString(key: String, value: String) {
        if (stringMap[key] != value) {
            stringMap[key] = value
            writeStringToDatabase(key, value)
        }
    }

    private fun getLong(key: String, defaultValue: Long): Long {
        return longMap.getOrPut(key) { readStringFromDatabase(key)?.toLongOrNull() ?: defaultValue }
    }

    private fun setLong(key: String, value: Long) {
        if (longMap[key] != value) {
            longMap[key] = value
            writeStringToDatabase(key, value.toString())
        }
    }

    private fun getInt(key: String, defaultValue: Int): Int {
        return intMap.getOrPut(key) { readStringFromDatabase(key)?.toIntOrNull() ?: defaultValue }
    }

    private fun setInt(key: String, value: Int) {
        if (intMap[key] != value) {
            intMap[key] = value
            writeStringToDatabase(key, value.toString())
        }
    }

    private fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return booleanMap.getOrPut(key) { readStringFromDatabase(key)?.toBooleanStrictOrNull() ?: defaultValue }
    }

    private fun setBoolean(key: String, value: Boolean) {
        if (booleanMap[key] != value) {
            booleanMap[key] = value
            writeStringToDatabase(key, value.toString())
        }
    }

    private fun getDouble(key: String, defaultValue: Double): Double {
        return doubleMap.getOrPut(key) { readStringFromDatabase(key)?.toDoubleOrNull() ?: defaultValue }
    }

    private fun setDouble(key: String, value: Double) {
        if (doubleMap[key] != value) {
            doubleMap[key] = value
            writeStringToDatabase(key, value.toString())
        }
    }

    //
    // Helper class
    class SettingInt(val key: String, val defaultValue: Int) {
        var value
            get() = getInt(key, defaultValue)
            set(newValue) = setInt(key, newValue)
    }

    class SettingString(val key: String, val defaultValue: String) {
        var value
            get() = getString(key, defaultValue)
            set(newValue) = setString(key, newValue)
    }

    class SettingLong(val key: String, val defaultValue: Long) {
        var value
            get() = getLong(key, defaultValue)
            set(newValue) = setLong(key, newValue)
    }

    class SettingBoolean(val key: String, val defaultValue: Boolean) {
        var value
            get() = getBoolean(key, defaultValue)
            set(newValue) = setBoolean(key, newValue)
    }

    class SettingDouble(val key: String, val defaultValue: Double) {
        var value
            get() = getDouble(key, defaultValue)
            set(newValue) = setDouble(key, newValue)
    }
}