package com.mdiwebma.base.utils

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mdiwebma.base.task.executeOnUiThread
import kotlinx.coroutines.Runnable

fun Context.showToast(message: String?, isLong: Boolean = false) {
    if (message.isNullOrEmpty()) return
    val duration = if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Toast.makeText(this, message, duration).show()
    } else {
        executeOnUiThread {
            Toast.makeText(this, message, duration).show()
        }
    }
}

fun Activity.runOnValidUiThread(runnable: Runnable) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        if (!isFinishing && !isDestroyed) {
            runnable.run()
        }
    } else {
        executeOnUiThread {
            if (!isFinishing && !isDestroyed) {
                runnable.run()
            }
        }
    }
}

fun Fragment.runOnValidUiThread(runnable: Runnable) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        if (isAdded && view != null) {
            runnable.run()
        }
    } else {
        executeOnUiThread {
            if (isAdded && view != null) {
                runnable.run()
            }
        }
    }
}