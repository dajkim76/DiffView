package com.mdiwebma.diffviewer.box

import android.content.Context
import android.util.Log
import com.mdiwebma.diffviewer.BuildConfig
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.converter.PropertyConverter

/**
 * Singleton manager for ObjectBox BoxStore in the demo app.
 * 백업은
 * boxStore.runInTx {
 *   // 이 블록 안에서 data.mdb 파일을 backup 위치로 복사
 *   // adb shell run-as com.mdiwebma.diffviewer ls -la files/objectbox/app-db/
 *   File(dbDir, "data.mdb").copyTo(File(backupDir, "data.mdb"), overwrite = true)
 * }
 */
class AppBoxStore private constructor(applicationContext: Context) {
    val boxStore: BoxStore = MyObjectBox.builder()
        .androidContext(applicationContext)
        .name("app-db")
        .build().also {
            if (BuildConfig.DEBUG) {
                /**
                 *   1. 기기/에뮬레이터가 연결된 상태에서 터미널에 포트 포워딩 명령어를 실행합니다:
                 *     adb forward tcp:8090 tcp:8090
                 *
                 *   2. PC 웹 브라우저(Chrome 등)에서 접속합니다:
                 *   👉 http://localhost:8090/index.html
                 */
                try {
                    // release 모드에서 컴파일 오류를 피하기 위해 리플렉션 사용
                    val adminClass = Class.forName("io.objectbox.android.Admin")
                    val constructor = adminClass.getConstructor(BoxStore::class.java)
                    val adminInstance = constructor.newInstance(it)
                    val startMethod = adminClass.getMethod("start", android.content.Context::class.java)
                    val started = startMethod.invoke(adminInstance, applicationContext) as? Boolean ?: false
                    Log.d(TAG, "ObjectBox Admin (Object Browser) started: $started (port: 8090)")
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to start ObjectBox Admin: ${e.message}")
                }
            }
        }

    fun isInitialized(): Boolean = !boxStore.isClosed

    @Synchronized
    fun close() {
        if (!boxStore.isClosed) boxStore.close()
    }

    fun <T> getBox(entityClass: Class<T>): Box<T> = boxStore.boxFor(entityClass)

    inline fun <reified T> getBox(): Box<T> = boxStore.boxFor(T::class.java)

    companion object {
        private const val TAG = "AppBoxStore"

        @Volatile
        private var instance: AppBoxStore? = null

        fun getInstance(context: Context): AppBoxStore {
            return instance ?: synchronized(this) {
                instance ?: AppBoxStore(context.applicationContext).also {
                    instance = it
                }
            }
        }

        @Synchronized
        fun close() {
            instance?.close()
            instance = null
        }
    }
}

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

class EmptyStringMigration : PropertyConverter<String, String?> {
    override fun convertToEntityProperty(databaseValue: String?): String {
        return databaseValue.orEmpty()
    }

    override fun convertToDatabaseValue(entityProperty: String): String {
        return entityProperty
    }
}