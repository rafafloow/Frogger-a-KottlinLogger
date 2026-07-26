package dev.rafaflow.klog.core

internal data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val threadName: String,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
)
