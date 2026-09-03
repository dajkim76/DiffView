package com.mdiwebma.diffviewer

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

class MyApp : Application() {

    override fun onCreate() {
        instance = this
        super.onCreate()
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: Context? = null

        fun setAppContext(context: Context) {
            if (instance == null) {
                instance = context.applicationContext
            }
        }

        val appContext: Context
            get() {
                return instance ?: error("MyApp.instance is null. Should call MyApp.setAppContext()")
            }
    }
}