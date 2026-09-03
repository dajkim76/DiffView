package com.mdiwebma.diffviewer.utils

import kotlinx.coroutines.Runnable
import java.util.concurrent.Executors

object CommonExecutors {
    private val executors = Executors.newCachedThreadPool()

    fun execute(runnable: Runnable) {
        executors.execute(runnable)
    }
}