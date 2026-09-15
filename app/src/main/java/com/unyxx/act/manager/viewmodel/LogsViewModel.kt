package com.unyxx.act.manager.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unyxx.act.manager.di.ServiceLocator
import com.unyxx.act.xposed.prefs.PrefsSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogsViewModel : ViewModel() {

    private val _events = MutableStateFlow<List<PrefsSchema.LogEvent>>(emptyList())
    val events: StateFlow<List<PrefsSchema.LogEvent>> = _events.asStateFlow()

    private val _lastSequenceId = MutableStateFlow(ServiceLocator.getLastSequenceId())
    val lastSequenceId: StateFlow<Long> = _lastSequenceId.asStateFlow()

    private var _isAutoScroll = true
    val isAutoScroll: Boolean get() = _isAutoScroll

    init {
        refresh()
    }

    /** Load initial batch of all events. */
    fun refresh() {
        viewModelScope.launch {
            val all = ServiceLocator.getAllLogEvents()
            _events.value = all
            if (all.isNotEmpty()) {
                _lastSequenceId.value = all.first().sequenceId
            }
        }
    }

    /** Poll for new events since [lastSequenceId]. Used for realtime stream. */
    fun pollNewEvents() {
        viewModelScope.launch {
            val lastId = _lastSequenceId.value
            val newEvents = ServiceLocator.getEventsBatch(lastId, limit = 50)
            if (newEvents.isNotEmpty()) {
                val current = _events.value.toMutableList()
                current.addAll(0, newEvents)
                _events.value = current.take(2000)
                _lastSequenceId.value = newEvents.first().sequenceId
            }
        }
    }

    fun setAutoScroll(enabled: Boolean) {
        _isAutoScroll = enabled
    }

    fun clear() {
        ServiceLocator.clearLogEvents()
        _events.value = emptyList()
    }
}
