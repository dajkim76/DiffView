package com.mdiwebma.base.debug

import com.mdiwebma.base.helper.Lx

object DUtils {

    @JvmStatic
    fun notReached(throwable: Throwable) {
        Lx("notReached: ${throwable.message}", lines = 5)
    }

    @JvmStatic
    fun exception(throwable: Throwable, tag: String, callstack: String) {
        Lx("exception: ${throwable.message}", lines = 5)
    }
}