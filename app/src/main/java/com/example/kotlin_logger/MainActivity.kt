package com.example.kotlin_logger

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dev.rafaflow.klog.core.KLog
import dev.rafaflow.klog.core.KLogConfig
import dev.rafaflow.klog.core.LogFormat
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Small manual test bench for the zero-setup logger.
 */
class MainActivity : AppCompatActivity() {
    private val backgroundExecutor = Executors.newFixedThreadPool(CONCURRENT_WORKERS)
    private var currentFormat = LogFormat.MARKDOWN
    private lateinit var statusView: TextView
    private lateinit var formatButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.text_status)
        formatButton = findViewById(R.id.button_format)

        findViewById<Button>(R.id.button_info).setOnClickListener {
            KLog.i(DEMO_TAG, "Manual INFO generated from the demo")
            showOperation("INFO registrado")
        }
        findViewById<Button>(R.id.button_warning).setOnClickListener {
            KLog.w(DEMO_TAG, "Manual WARNING generated from the demo")
            showOperation("WARNING registrado")
        }
        findViewById<Button>(R.id.button_error).setOnClickListener {
            val sampleError = IllegalStateException("Controlled demo exception")
            KLog.e(DEMO_TAG, "Manual ERROR generated from the demo", sampleError)
            showOperation("ERROR con stacktrace registrado")
        }
        formatButton.setOnClickListener {
            currentFormat = when (currentFormat) {
                LogFormat.MARKDOWN -> LogFormat.TEXT
                LogFormat.TEXT -> LogFormat.MARKDOWN
            }
            KLog.configure(demoConfig())
            updateFormatButton()
            showOperation("Formato cambiado a ${currentFormat.name}")
        }
        findViewById<Button>(R.id.button_concurrent).setOnClickListener {
            generateConcurrentLogs()
        }
        findViewById<Button>(R.id.button_refresh).setOnClickListener {
            refreshReportSummary()
        }
        findViewById<Button>(R.id.button_clear).setOnClickListener {
            KLog.clearLogs()
            showOperation("Reportes eliminados")
        }
        findViewById<Button>(R.id.button_crash).setOnClickListener {
            confirmIntentionalCrash()
        }

        KLog.configure(demoConfig())
        updateFormatButton()
        refreshReportSummary()
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) refreshReportSummary()
    }

    override fun onDestroy() {
        backgroundExecutor.shutdown()
        super.onDestroy()
    }

    private fun demoConfig(): KLogConfig = KLogConfig(
        logFormat = currentFormat,
        customDeviceMetadata = mapOf(
            "Demo" to "Button test bench",
            "Build mode" to if (isDebuggable()) "debuggable" else "release",
        ),
    )

    private fun isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun updateFormatButton() {
        formatButton.text = getString(R.string.active_format, currentFormat.name)
    }

    private fun generateConcurrentLogs() {
        statusView.text = resources.getQuantityString(
            R.plurals.generating_concurrent_logs,
            CONCURRENT_LOG_COUNT,
            CONCURRENT_LOG_COUNT,
        )
        val completed = AtomicInteger()

        repeat(CONCURRENT_LOG_COUNT) { index ->
            backgroundExecutor.execute {
                KLog.i("ConcurrentWorker", "Concurrent entry #$index")
                if (completed.incrementAndGet() == CONCURRENT_LOG_COUNT) {
                    runOnUiThread {
                        showOperation("$CONCURRENT_LOG_COUNT logs concurrentes registrados")
                    }
                }
            }
        }
    }

    private fun showOperation(message: String) {
        refreshReportSummary(message)
    }

    private fun refreshReportSummary(operation: String? = null) {
        backgroundExecutor.execute {
            val files = KLog.getLogFiles()
            val summary = buildSummary(operation, files)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) statusView.text = summary
            }
        }
    }

    private fun buildSummary(operation: String?, files: List<File>): String = buildString {
        operation?.let {
            appendLine(it)
            appendLine()
        }
        appendLine("Formato configurado: ${currentFormat.name}")
        appendLine("Reportes encontrados: ${files.size}")
        files.forEach { file ->
            appendLine("• ${file.name} — ${file.length()} bytes")
        }

        files.firstOrNull()?.let { latest ->
            appendLine()
            appendLine("Vista previa de ${latest.name}:")
            appendLine("--------------------------------")
            append(readPreview(latest))
        }
    }

    private fun readPreview(file: File): String = try {
        file.readText()
            .takeLast(PREVIEW_CHARACTER_LIMIT)
    } catch (exception: Exception) {
        "No se pudo leer el reporte: ${exception.message}"
    }

    private fun confirmIntentionalCrash() {
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_dialog_title)
            .setMessage(R.string.crash_dialog_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.crash_now) { _, _ ->
                throw IllegalStateException(INTENTIONAL_CRASH_MESSAGE)
            }
            .show()
    }

    private companion object {
        const val DEMO_TAG = "KLogDemo"
        const val INTENTIONAL_CRASH_MESSAGE = "Intentional crash from KLog Demo"
        const val CONCURRENT_WORKERS = 4
        const val CONCURRENT_LOG_COUNT = 40
        const val PREVIEW_CHARACTER_LIMIT = 4_000
    }
}
