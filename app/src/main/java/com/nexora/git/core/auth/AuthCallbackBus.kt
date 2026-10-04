package com.nexora.git.core.auth

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class AuthCallbackBus @Inject constructor() {
    private val mutableCallbacks = MutableSharedFlow<Uri>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val callbacks: SharedFlow<Uri> = mutableCallbacks.asSharedFlow()

    fun dispatch(uri: Uri) {
        mutableCallbacks.tryEmit(uri)
    }
}
