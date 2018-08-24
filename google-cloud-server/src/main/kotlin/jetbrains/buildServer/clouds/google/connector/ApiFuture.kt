package jetbrains.buildServer.clouds.google.connector

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.common.util.concurrent.*
import kotlinx.coroutines.experimental.*
import kotlin.coroutines.experimental.Continuation

/**
 * Awaits for completion of the future without blocking a thread.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is completed while this suspending function is waiting, this function
 * stops waiting for the future and immediately resumes with [CancellationException].
 *
 * Note, that `ListenableFuture` does not support removal of installed listeners, so on cancellation of this wait
 * a few small objects will remain in the `ListenableFuture` list of listeners until the future completes. However, the
 * care is taken to clear the reference to the waiting coroutine itself, so that its memory can be released even if
 * the future never completes.
 */
suspend fun <T> ApiFuture<T>.await(): T = suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
    val callback = ContinuationCallback(cont)
    ApiFutures.addCallback(this, callback, MoreExecutors.directExecutor())
    cont.invokeOnCancellation {
        callback.cont = null // clear the reference to continuation from the future's callback
    }
}

private class ContinuationCallback<T>(
        @Volatile @JvmField var cont: Continuation<T>?
) : ApiFutureCallback<T> {
    override fun onSuccess(result: T?) {
        @Suppress("UNCHECKED_CAST")
        cont?.resume(result as T)
    }
    override fun onFailure(t: Throwable) { cont?.resumeWithException(t) }
}