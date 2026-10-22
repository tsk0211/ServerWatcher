package dev.tsk.server.watcher

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import dagger.hilt.android.HiltAndroidApp

private const val TAG = "SW"

@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "[App] onCreate — loading SQLCipher native library")
        System.loadLibrary("sqlcipher")
        Log.d(TAG, "[App] SQLCipher native library loaded")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "[CRASH] thread=${thread.name} → ${throwable::class.qualifiedName}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.d(TAG, "[App] ready")
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
