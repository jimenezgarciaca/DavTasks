package org.tasks.http

import java.net.ConnectException

internal actual fun Exception.translated(): Exception = when (this) {
    is ConnectException -> ConnectionException(message, this)
    else -> this
}
