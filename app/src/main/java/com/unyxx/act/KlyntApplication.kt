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
        installCrashCatcher()
        ServiceLocator.init(this)
        try {
            XposedServiceHelper.registerListener(this)
        } catch (_: Throwable) {
            // No framework present — manager still works standalone.
        }
    }

    /**
     * Last-resort crash recorder. If the manager ever dies, the stacktrace
     * lands in `filesDir/crash.log` (surfaced in Settings diagnostics) so
     * a crash is evidence instead of a mystery. Always chains to the
     * previous handler so system crash UX is unchanged.
     */
    private fun installCrashCatcher() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = java.io.File(filesDir, "crash.log")
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                file.writeText(
                    "${java.util.Date()} ${thread.name}\n${sw}\n"
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
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
