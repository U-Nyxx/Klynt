package com.unyxx.act.manager.viewmodel

import androidx.lifecycle.ViewModel
import com.unyxx.act.manager.di.ServiceLocator
import kotlinx.coroutines.flow.StateFlow

/** UI state source for Logs (manager-side event history). */
class LogsViewModel : ViewModel() {

    val events: StateFlow<List<ServiceLocator.Event>> = ServiceLocator.events

    fun clear() {
        ServiceLocator.clearEvents()
    }
}
