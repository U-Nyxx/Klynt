package com.unyxx.act

import android.app.Application
import com.unyxx.act.manager.di.ServiceLocator

class KlyntApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}