package dev.rafaflow.klog.core

/**
 * Runtime configuration for KLog.
 *
 * Values outside their useful range are safely normalized internally so a bad
 * remote configuration cannot crash the host application.
 */
public data class KLogConfig(
    val maxLogAgeDays: Int = 7,
    val maxFolderSizeMb: Int = 10,
    val logFormat: LogFormat = LogFormat.MARKDOWN,
    val captureUncaughtCrashes: Boolean = true,
    val customDeviceMetadata: Map<String, String> = emptyMap(),
) {
    internal fun normalized(): KLogConfig = copy(
        maxLogAgeDays = maxLogAgeDays.coerceAtLeast(1),
        maxFolderSizeMb = maxFolderSizeMb.coerceAtLeast(1),
        customDeviceMetadata = customDeviceMetadata
            .filterKeys(String::isNotBlank)
            .mapValues { (_, value) -> value.take(MAX_METADATA_VALUE_LENGTH) },
    )

    private companion object {
        const val MAX_METADATA_VALUE_LENGTH: Int = 4_096
    }
}
