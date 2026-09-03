package com.mdiwebma.diffviewer

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

import androidx.appcompat.app.AppCompatDelegate

class MyApp : Application() {

    override fun onCreate() {
        instance = this
        super.onCreate()
        applyTheme(AppSettings.appTheme.value)
    }

    companion object {
        fun applyTheme(themeValue: String) {
            val mode = when (themeValue) {
                AppSettings.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppSettings.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }
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