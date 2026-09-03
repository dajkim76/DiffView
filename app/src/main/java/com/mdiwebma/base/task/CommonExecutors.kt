package com.mdiwebma.base.task

import android.os.Handler
import android.os.Looper
import com.mdiwebma.base.debug.DUtils.exception
import com.mdiwebma.base.helper.StringBuilderPool.obtain
import com.mdiwebma.base.helper.StringBuilderPool.recycle
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object CommonExecutors {
    private const val TAG = "CommonExecutors"
    val cachedThreadPool: ExecutorService = Executors.newCachedThreadPool()
    private val serialExecutor = SerialExecutor()
    private val scheduledThreadPool: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    val handler: Handler = Handler(Looper.getMainLooper())

    fun runOnUiThread(runnable: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            handler.post(runnable)
        }
    }

    fun postOnUiThread(runnable: Runnable) {
        handler.post(runnable)
    }

    fun postDelayedOnUiThread(delayMillis: Long, runnable: Runnable) {
        if (delayMillis > 0) {
            handler.postDelayed(runnable, delayMillis)
        } else {
            handler.post(runnable)
        }
    }

    fun execute(runnable: Runnable) {
        cachedThreadPool.execute(SafeRunnable(runnable, Thread.currentThread().getStackTrace()))
    }

    private fun getCallerStack(callerStackElement: Array<StackTraceElement>): String {
        val sb = obtain()
        for (e in callerStackElement) {
            if (!(e.getClassName() == "dalvik.system.VMStack") && !(e.getClassName() == "java.lang.Thread") && !(e.getClassName() == CommonExecutors::class.java.getName())) {
                sb.append(e.toString())
                sb.append("\n")
            }
        }
        return recycle(sb)
    }

    fun executeBySerial(runnable: Runnable) {
        serialExecutor.execute(SafeRunnable(runnable, Thread.currentThread().getStackTrace()))
    }

    val serialPendingTasksCount: Int
        get() = serialExecutor.pendingTasksCount

    fun clearSerialPendingTasks() {
        serialExecutor.clearPendingTasks()
    }

    fun executeBySchedule(runnable: Runnable, delay: Long, timeUnit: TimeUnit?) {
        scheduledThreadPool.schedule(SafeRunnable(runnable, Thread.currentThread().getStackTrace()), delay, timeUnit)
    }

    // from AsyncTask
    private open class SerialExecutor : Executor {
        val tasks: ArrayDeque<Runnable?> = ArrayDeque<Runnable?>()
        var activeRunnable: Runnable? = null

        @Synchronized
        override fun execute(runnable: Runnable) {
            tasks.offer(object : Runnable {
                override fun run() {
                    try {
                        runnable.run()
                    } finally {
                        scheduleNext()
                    }
                }
            })
            if (activeRunnable == null) {
                scheduleNext()
            }
        }

        @Synchronized
        protected fun scheduleNext() {
            if ((tasks.poll().also { activeRunnable = it }) != null) {
                cachedThreadPool.execute(activeRunnable)
            }
        }

        @get:Synchronized
        val pendingTasksCount: Int
            get() = tasks.size

        @Synchronized
        fun clearPendingTasks() {
            tasks.clear()
        }
    }

    private class SafeRunnable(val innerRunnable: Runnable, val callerStackElement: Array<StackTraceElement>) : Runnable {
        override fun run() {
            try {
                innerRunnable.run()
            } catch (err: OutOfMemoryError) {
                System.gc()
                exception(err, TAG, CommonExecutors.getCallerStack(callerStackElement))
            } catch (ex: Exception) {
                exception(ex, TAG, CommonExecutors.getCallerStack(callerStackElement))
            }
        }
    }
}

fun executeOnBackground(runnable: Runnable) = CommonExecutors.execute(runnable)

fun executeOnUiThread(runnable: Runnable) = CommonExecutors.handler.post(runnable)