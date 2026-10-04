package com.nexora.git.core.auth

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

@Singleton
class AuthCallbackBus @Inject constructor() {
    private val callbackChannel = Channel<Uri>(Channel.BUFFERED)

    val callbacks: Flow<Uri> = callbackChannel.receiveAsFlow()

    fun dispatch(uri: Uri) {
        callbackChannel.trySend(uri)
    }
}
