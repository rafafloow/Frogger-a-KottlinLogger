package dev.rafaflow.klog.core

/**
 * On-disk representation used for newly created reports.
 */
public enum class LogFormat(public val extension: String) {
    MARKDOWN("md"),
    TEXT("txt"),
}
