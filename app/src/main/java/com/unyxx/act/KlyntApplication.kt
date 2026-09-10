package com.unyxx.act

import android.app.Application
import com.unyxx.act.manager.di.ServiceLocator
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Hosts the libxposed service connection.
 *
 * When the manager app runs on a device with LSPosed active, the
 * framework binds [XposedService] here. Through it the manager writes
 * Remote Preferences (read by hooks via `getRemotePreferences`) and
 * queries the enabled scope — the reliable "module active" signal.
 */
class KlyntApplication : Application(), XposedServiceHelper.OnServiceListener {

    /** Listener for framework binder connect/disconnect events. */
    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    companion object {
        @Volatile
        var xposedService: XposedService? = null
            private set

        private val listeners = CopyOnWriteArraySet<ServiceStateListener>()

        fun addServiceStateListener(listener: ServiceStateListener, notifyImmediately: Boolean) {
            listeners.add(listener)
            if (notifyImmediately) listener.onServiceStateChanged(xposedService)
        }

        fun removeServiceStateListener(listener: ServiceStateListener) {
            listeners.remove(listener)
        }

        private fun dispatch(service: XposedService?) {
            listeners.forEach { it.onServiceStateChanged(service) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        try {
            XposedServiceHelper.registerListener(this)
        } catch (_: Throwable) {
            // No framework present — manager still works standalone.
        }
    }

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        dispatch(service)
    }

    override fun onServiceDied(service: XposedService) {
        if (xposedService === service) xposedService = null
        dispatch(null)
    }
}
