package com.example.bibleapp

import android.app.Application
import com.example.bibleapp.util.AppLogger

class BibleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
    }
}
