package com.mdiwebma.base.helper

import android.os.Looper
import android.util.Log
import com.mdiwebma.diffviewer.BuildConfig

object TraceHelper {
    private const val TAG = "__T"

    @JvmStatic
    fun e1(msg: String?) {
        if (BuildConfig.DEBUG) print(Log.ERROR, 1, msg)
    }

    @JvmStatic
    fun e(msg: String?) {
        if (BuildConfig.DEBUG) print(Log.ERROR, msg)
    }

    @JvmStatic
    fun w(msg: String?) {
        if (BuildConfig.DEBUG) print(Log.WARN, msg)
    }

    @JvmStatic
    fun i(msg: String?) {
        if (BuildConfig.DEBUG) print(Log.INFO, msg)
    }

    @JvmStatic
    fun d(msg: String?) {
        if (BuildConfig.DEBUG) print(Log.DEBUG, msg)
    }

    @JvmStatic
    fun v(msg: String?) {
        if (BuildConfig.DEBUG) print(Log.VERBOSE, msg)
    }

    @JvmStatic
    fun print(level: Int, msg: String?) {
        if (BuildConfig.DEBUG) print(level, level - 1, msg)
    }

    @JvmStatic
    fun print(level: Int, lines: Int, msg: String?) {
        if (!BuildConfig.DEBUG) return
        val sb = StringBuilder()
        sb.append(msg)
        if (lines > 2) {
            sb.append("\n")
        }
        var count = 1
        for (e in Thread.currentThread().getStackTrace()) {
            val trace = e.toString()
            if (!trace.contains("VMStack.getThreadStackTrace")
                && !trace.contains("java.lang.Thread.getStackTrace")
                && !trace.contains(TraceHelper::class.java.getPackage()!!.name)
            ) {
                sb.append("  at ").append(trace)
                sb.append("\n")
                count++
                if (count > lines) {
                    break
                }
            }
        }

        when (level) {
            Log.ERROR -> Log.e(TAG, sb.toString())
            Log.WARN -> Log.w(TAG, sb.toString())
            Log.INFO -> Log.i(TAG, sb.toString())
            Log.DEBUG -> Log.d(TAG, sb.toString())
            Log.VERBOSE -> Log.v(TAG, sb.toString())
        }
    }

    @JvmStatic
    fun assertUiThread() {
        if (!BuildConfig.DEBUG) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw AssertionError("assert Ui Thread")
        }
    }
}


fun Lx(msg: String?, level: Int = Log.ERROR, lines: Int = 1) = TraceHelper.print(level, lines, msg)
fun Le(msg: String?, lines: Int = 5) = TraceHelper.print(Log.ERROR, lines, msg)
fun Lw(msg: String?, lines: Int = 4) = TraceHelper.print(Log.WARN, lines, msg)
fun Li(msg: String?, lines: Int = 3) = TraceHelper.print(Log.INFO, lines, msg)
fun Ld(msg: String?, lines: Int = 2) = TraceHelper.print(Log.DEBUG, lines, msg)
fun Lv(msg: String?, lines: Int = 1) = TraceHelper.print(Log.VERBOSE, lines, msg)