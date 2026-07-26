package dev.rafaflow.klog.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persists fatal exceptions synchronously, then hands control back to Android.
 */
internal class KLogCrashHandler(
    internal val delegate: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    private val handlingCrash = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (handlingCrash.compareAndSet(false, true)) {
            try {
                KLog.recordCrash(thread, throwable)
            } catch (_: Throwable) {
                // A logger must never hide or replace the original fatal error.
            }
        }

        if (delegate !== this) {
            delegate?.uncaughtException(thread, throwable)
        }
    }
}
