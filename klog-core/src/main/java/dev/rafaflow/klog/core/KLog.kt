package dev.rafaflow.klog.core

import android.content.Context
import java.io.File

/**
 * Public, zero-setup facade for recording and managing reports.
 */
public object KLog {
    private val lock = Any()

    @Volatile
    private var config: KLogConfig = KLogConfig()

    @Volatile
    private var writer: LogFileWriter? = null

    @Volatile
    private var crashHandler: KLogCrashHandler? = null

    internal fun initialize(context: Context) {
        synchronized(lock) {
            if (writer == null) {
                writer = LogFileWriter(context.applicationContext, config)
            }
            updateCrashHandlerLocked()
        }
    }

    /**
     * Replaces the current runtime configuration. Existing files keep their
     * original format; future entries use [newConfig].
     */
    @JvmStatic
    public fun configure(newConfig: KLogConfig) {
        synchronized(lock) {
            config = newConfig.normalized()
            writer?.updateConfig(config)
            updateCrashHandlerLocked()
        }
    }

    @JvmStatic
    public fun i(tag: String, msg: String) {
        record(LogLevel.INFO, tag, msg)
    }

    @JvmStatic
    public fun w(tag: String, msg: String) {
        record(LogLevel.WARN, tag, msg)
    }

    @JvmStatic
    @JvmOverloads
    public fun e(tag: String, msg: String, throwable: Throwable? = null) {
        record(LogLevel.ERROR, tag, msg, throwable)
    }

    /**
     * Returns newest reports first. Files live in app-private storage.
     */
    @JvmStatic
    public fun getLogFiles(): List<File> = writer?.files().orEmpty()

    @JvmStatic
    public fun clearLogs() {
        writer?.clear()
    }

    internal fun recordCrash(thread: Thread, throwable: Throwable) {
        writer?.write(
            LogEntry(
                timestampMillis = System.currentTimeMillis(),
                level = LogLevel.CRASH,
                threadName = thread.name,
                tag = CRASH_TAG,
                message = throwable.message ?: throwable::class.java.name,
                throwable = throwable,
            ),
        )
    }

    private fun record(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        writer?.write(
            LogEntry(
                timestampMillis = System.currentTimeMillis(),
                level = level,
                threadName = Thread.currentThread().name,
                tag = tag,
                message = message,
                throwable = throwable,
            ),
        )
    }

    private fun updateCrashHandlerLocked() {
        val installed = crashHandler
        if (config.captureUncaughtCrashes) {
            if (installed == null) {
                val current = Thread.getDefaultUncaughtExceptionHandler()
                if (current is KLogCrashHandler) {
                    crashHandler = current
                } else {
                    KLogCrashHandler(current).also { handler ->
                        crashHandler = handler
                        Thread.setDefaultUncaughtExceptionHandler(handler)
                    }
                }
            }
        } else if (installed != null) {
            if (Thread.getDefaultUncaughtExceptionHandler() === installed) {
                Thread.setDefaultUncaughtExceptionHandler(installed.delegate)
            }
            crashHandler = null
        }
    }

    private const val CRASH_TAG = "UncaughtException"
}
