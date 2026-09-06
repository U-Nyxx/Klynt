package com.unyxx.act.util

import android.util.Log
import timber.log.Timber

object Logger {
    @JvmField val TAG = "KLYNT"

    fun init(modulePath: String?) {
        Timber.plant(object : Timber.DebugTree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                val tagStr = tag ?: TAG
                val msg = "$tagStr: $message"
                when (priority) {
                    Log.VERBOSE -> Log.v(tagStr, msg, t)
                    Log.DEBUG -> Log.d(tagStr, msg, t)
                    Log.INFO -> Log.i(tagStr, msg, t)
                    Log.WARN -> Log.w(tagStr, msg, t)
                    Log.ERROR -> Log.e(tagStr, msg, t)
                    Log.ASSERT -> Log.wtf(tagStr, msg, t)
                }
            }
        })
        Timber.d("Logger initialized, module path: $modulePath")
    }

    inline fun v(crossinline msg: () -> String) = Timber.tag(TAG).v(msg())
    inline fun d(crossinline msg: () -> String) = Timber.tag(TAG).d(msg())
    inline fun i(crossinline msg: () -> String) = Timber.tag(TAG).i(msg())
    inline fun w(crossinline msg: () -> String) = Timber.tag(TAG).w(msg())
    inline fun e(crossinline msg: () -> String) = Timber.tag(TAG).e(msg())
    inline fun wtf(crossinline msg: () -> String) = Timber.tag(TAG).wtf(msg())
}
