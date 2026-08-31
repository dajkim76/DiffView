package com.mdiwebma.diffview_demo.box

import android.content.Context
import android.util.Log
import com.mdiwebma.diffview_demo.BuildConfig
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.android.Admin

/**
 * Singleton manager for ObjectBox BoxStore in the demo app.
 */
class AppBoxStore private constructor(applicationContext: Context) {
    val boxStore: BoxStore = MyObjectBox.builder()
        .androidContext(applicationContext)
        .name("main-db")
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
                    val started = Admin(it).start(applicationContext)
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
