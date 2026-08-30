package com.luzzymeow.luzzyrp.core.common

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp Call 的协程桥接。
 *
 * 参考 rikkahub common 模块的同名实现：通过 suspendCancellableCoroutine
 * 将异步 enqueue 转为挂起调用，取消协程时同步取消底层 Call。
 */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    val callback = object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    }

    enqueue(callback)
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
}
