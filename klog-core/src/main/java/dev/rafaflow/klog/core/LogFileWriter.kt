package dev.rafaflow.klog.core

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Synchronous, thread-safe storage engine. Crash writes therefore finish before
 * control returns to Android's default uncaught-exception handler.
 */
internal class LogFileWriter(
    context: Context,
    initialConfig: KLogConfig,
) {
    private val appContext = context.applicationContext
    private val reportsDirectory = File(appContext.filesDir, REPORTS_DIRECTORY)
    private var config = initialConfig.normalized()
    private var activeFile: File? = null
    private var sessionToken: String = newSessionToken()

    @Synchronized
    fun updateConfig(newConfig: KLogConfig) {
        val normalized = newConfig.normalized()
        val reportIdentityChanged =
            normalized.logFormat != config.logFormat ||
                normalized.customDeviceMetadata != config.customDeviceMetadata

        config = normalized
        if (reportIdentityChanged) {
            activeFile = null
            sessionToken = newSessionToken()
        }
        rotateSafely()
    }

    @Synchronized
    fun write(entry: LogEntry) {
        try {
            if (!reportsDirectory.exists() && !reportsDirectory.mkdirs()) return
            rotate()

            val destination = activeFile()
            val isNewReport = !destination.exists() || destination.length() == 0L
            FileOutputStream(destination, true)
                .bufferedWriter(Charsets.UTF_8)
                .use { writer ->
                if (isNewReport) {
                    writer.append(reportHeader(config.logFormat))
                }
                writer.append(format(entry, config.logFormat))
            }
            rotate()
        } catch (_: Exception) {
            // I/O, permissions, and formatting failures must not affect the app.
        }
    }

    @Synchronized
    fun files(): List<File> {
        rotateSafely()
        return try {
            reportFiles().sortedByDescending(File::lastModified)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clear() {
        try {
            reportFiles().forEach { file ->
                try {
                    file.delete()
                } catch (_: SecurityException) {
                    // Best effort cleanup.
                }
            }
        } catch (_: Exception) {
            // Best effort cleanup.
        }
        activeFile = null
        sessionToken = newSessionToken()
    }

    private fun activeFile(): File {
        activeFile?.let { return it }
        return File(
            reportsDirectory,
            "klog_$sessionToken.${config.logFormat.extension}",
        ).also { activeFile = it }
    }

    private fun reportHeader(format: LogFormat): String {
        val metadata = deviceMetadata()
        return when (format) {
            LogFormat.MARKDOWN -> buildString {
                appendLine("# KLog report")
                appendLine()
                appendLine("## Device and application")
                appendLine()
                metadata.forEach { (key, value) ->
                    append("- **")
                    append(escapeMarkdown(key))
                    append(":** ")
                    appendLine(escapeMarkdown(value))
                }
                appendLine()
                appendLine("## Entries")
                appendLine()
                appendLine("| Timestamp (UTC) | Level | Thread | Tag | Message |")
                appendLine("|---|---|---|---|---|")
            }

            LogFormat.TEXT -> buildString {
                appendLine("KLog report")
                appendLine("===========")
                metadata.forEach { (key, value) ->
                    append(key.replaceLineBreaks())
                    append(": ")
                    appendLine(value.replaceLineBreaks())
                }
                appendLine()
                appendLine("Entries")
                appendLine("-------")
            }
        }
    }

    private fun format(entry: LogEntry, format: LogFormat): String = when (format) {
        LogFormat.MARKDOWN -> formatMarkdown(entry)
        LogFormat.TEXT -> formatText(entry)
    }

    private fun formatMarkdown(entry: LogEntry): String = buildString {
        val timestamp = recordTimestamp(entry.timestampMillis)
        append("| ")
        append(timestamp)
        append(" | ")
        append(entry.level.name)
        append(" | ")
        append(escapeTableCell(entry.threadName))
        append(" | ")
        append(escapeTableCell(entry.tag))
        append(" | ")
        append(escapeTableCell(entry.message))
        appendLine(" |")

        entry.throwable?.let { throwable ->
            appendLine()
            append("### ")
            append(entry.level.name.lowercase().replaceFirstChar(Char::uppercase))
            append(" — ")
            append(timestamp)
            append(" — ")
            appendLine(escapeMarkdown(entry.tag))
            appendLine()
            appendLine("```kotlin")
            appendLine(stackTrace(throwable).trimEnd())
            appendLine("```")
            appendLine()
        }
    }

    private fun formatText(entry: LogEntry): String = buildString {
        append(recordTimestamp(entry.timestampMillis))
        append(" [")
        append(entry.level.name)
        append("] [")
        append(entry.threadName.replaceLineBreaks())
        append("] ")
        append(entry.tag.replaceLineBreaks())
        append(": ")
        appendLine(entry.message.replaceLineBreaks())
        entry.throwable?.let { throwable ->
            appendLine(stackTrace(throwable).trimEnd())
            appendLine()
        }
    }

    private fun deviceMetadata(): Map<String, String> = linkedMapOf(
        "Package" to appContext.packageName,
        "App version" to appVersion(),
        "Manufacturer" to Build.MANUFACTURER.orEmpty(),
        "Model" to Build.MODEL.orEmpty(),
        "Android" to Build.VERSION.RELEASE.orEmpty(),
        "SDK" to Build.VERSION.SDK_INT.toString(),
    ) + config.customDeviceMetadata

    @Suppress("DEPRECATION")
    private fun appVersion(): String = try {
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        "${packageInfo.versionName ?: "unknown"} ($versionCode)"
    } catch (_: Exception) {
        "unknown"
    }

    private fun rotateSafely() {
        try {
            rotate()
        } catch (_: Exception) {
            // Retention is best effort.
        }
    }

    private fun rotate() {
        if (!reportsDirectory.exists()) return

        val cutoff = System.currentTimeMillis() - config.maxLogAgeDays * MILLIS_PER_DAY
        reportFiles()
            .filter { it.lastModified() < cutoff }
            .forEach(::deleteAndForget)

        var files = reportFiles().sortedBy(File::lastModified)
        var totalBytes = files.sumOf(File::length)
        val maxBytes = config.maxFolderSizeMb.toLong() * BYTES_PER_MEGABYTE
        var index = 0

        while (totalBytes > maxBytes && index < files.size) {
            val file = files[index++]
            val fileBytes = file.length()
            if (deleteAndForget(file)) {
                totalBytes -= fileBytes
            }
        }
    }

    private fun deleteAndForget(file: File): Boolean {
        val deleted = try {
            file.delete()
        } catch (_: SecurityException) {
            false
        }
        if (deleted && activeFile == file) {
            activeFile = null
            sessionToken = newSessionToken()
        }
        return deleted
    }

    private fun reportFiles(): List<File> = reportsDirectory
        .listFiles { file -> file.isFile && file.extension in SUPPORTED_EXTENSIONS }
        ?.toList()
        .orEmpty()

    private fun recordTimestamp(timestampMillis: Long): String =
        requireNotNull(RECORD_DATE_FORMAT.get()).format(Date(timestampMillis))

    private fun newSessionToken(): String =
        "${requireNotNull(FILE_DATE_FORMAT.get()).format(Date())}_" +
            UUID.randomUUID().toString().take(8)

    private fun stackTrace(throwable: Throwable): String =
        StringWriter().also { buffer ->
            PrintWriter(buffer).use(throwable::printStackTrace)
        }.toString()

    private fun escapeTableCell(value: String): String = escapeMarkdown(value)
        .replace("\r\n", "<br>")
        .replace("\n", "<br>")
        .replace("\r", "<br>")

    private fun escapeMarkdown(value: String): String = value
        .replace("\\", "\\\\")
        .replace("|", "\\|")

    private fun String.replaceLineBreaks(): String = replace(Regex("[\\r\\n]+"), " ")

    private companion object {
        const val REPORTS_DIRECTORY = "klog_reports"
        const val BYTES_PER_MEGABYTE = 1_024L * 1_024L
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
        val SUPPORTED_EXTENSIONS = setOf("md", "txt")

        val RECORD_DATE_FORMAT: ThreadLocal<SimpleDateFormat> =
            object : ThreadLocal<SimpleDateFormat>() {
                override fun initialValue(): SimpleDateFormat =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
            }

        val FILE_DATE_FORMAT: ThreadLocal<SimpleDateFormat> =
            object : ThreadLocal<SimpleDateFormat>() {
                override fun initialValue(): SimpleDateFormat =
                    SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
            }
    }
}
