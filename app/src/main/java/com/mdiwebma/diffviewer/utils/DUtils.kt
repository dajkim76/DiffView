package com.mdiwebma.diffviewer.utils

import android.util.Log

object DUtils {

    fun notReached(throwable: Throwable) {
        Log.e("__T", "notReached: ${throwable.message}", throwable)
    }
}