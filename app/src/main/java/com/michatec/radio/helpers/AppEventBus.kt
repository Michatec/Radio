package com.michatec.radio.helpers

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface AppEvent {
    data class CollectionChanged(val modificationDateMillis: Long) : AppEvent
    data object RemoteServerError : AppEvent
    data class RemoteAuthFailed(val ip: String) : AppEvent
}

object AppEventBus {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    fun emit(event: AppEvent) {
        _events.tryEmit(event)
    }
}
