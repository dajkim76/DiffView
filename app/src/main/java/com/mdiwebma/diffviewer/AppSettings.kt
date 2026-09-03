package com.mdiwebma.diffviewer

import com.mdiwebma.diffviewer.box.AppBoxStore
import com.mdiwebma.diffviewer.box.SettingEntry
import com.mdiwebma.diffviewer.box.SettingEntry_
import java.util.concurrent.Executors

object AppSettings {
    // Declare App settings
    val runCount = SettingLong("runCount", 0)

    //
    // Database operation
    private val dbExecutor = Executors.newSingleThreadExecutor()

    private fun readString(key: String): String? {
        val box = AppBoxStore.getInstance(MyApp.appContext).getBox<SettingEntry>()
        return box.query(SettingEntry_.key.equal(key)).build().use { query ->
            query.findFirst()?.value
        }
    }

    private fun writeString(key: String, value: String) {
        dbExecutor.execute {
            val appBoxStore = AppBoxStore.getInstance(MyApp.appContext)
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

    //
    // Helper class
    abstract class SettingValue<T>(val key: String, val defaultValue: T) {
        @Volatile
        private var cachedValue: T? = null

        protected abstract fun parse(str: String): T?

        var value: T
            get() = cachedValue ?: (readString(key)?.let { parse(it) } ?: defaultValue).also {
                cachedValue = it
            }
            set(newValue) {
                if (cachedValue != newValue) {
                    cachedValue = newValue
                    writeString(key, newValue.toString())
                }
            }

        // 💡 위임 프로퍼티 지원 연산자
        // operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
        // operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) { value = newValue }

        init {
            if (BuildConfig.DEBUG) {
                if (!settingInstanceChecker.add(key)) error("SettingValue key is duplicate: $key")
            }
        }
    }

    class SettingInt(key: String, defaultValue: Int) : SettingValue<Int>(key, defaultValue) {
        override fun parse(str: String) = str.toIntOrNull()
    }

    class SettingLong(key: String, defaultValue: Long) : SettingValue<Long>(key, defaultValue) {
        override fun parse(str: String) = str.toLongOrNull()
    }

    class SettingBoolean(key: String, defaultValue: Boolean) : SettingValue<Boolean>(key, defaultValue) {
        override fun parse(str: String) = str.toBooleanStrictOrNull()
    }

    class SettingDouble(key: String, defaultValue: Double) : SettingValue<Double>(key, defaultValue) {
        override fun parse(str: String) = str.toDoubleOrNull()
    }

    class SettingString(key: String, defaultValue: String) : SettingValue<String>(key, defaultValue) {
        override fun parse(str: String) = str
    }
}

private val settingInstanceChecker = mutableSetOf<String>()