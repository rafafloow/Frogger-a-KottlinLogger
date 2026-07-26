package com.example.kotlin_logger

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rafaflow.klog.core.KLog
import dev.rafaflow.klog.core.KLogConfig
import dev.rafaflow.klog.core.LogFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end storage tests running against the real application context.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Before
    fun setUp() {
        KLog.clearLogs()
        KLog.configure(KLogConfig())
    }

    @After
    fun tearDown() {
        KLog.clearLogs()
        KLog.configure(KLogConfig())
    }

    @Test
    fun contentProviderAutoInitializesKLog() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        KLog.i("AutoInitTest", "Provider initialized before the test")

        assertEquals("com.example.kotlin_logger", appContext.packageName)
        assertTrue(KLog.getLogFiles().isNotEmpty())
    }

    @Test
    fun markdownReportContainsMetadataAndExceptionStackTrace() {
        KLog.configure(
            KLogConfig(
                logFormat = LogFormat.MARKDOWN,
                customDeviceMetadata = mapOf("Test suite" to "instrumented"),
            ),
        )

        KLog.e(
            tag = "MarkdownTest",
            msg = "Controlled failure",
            throwable = IllegalArgumentException("Expected test exception"),
        )

        val report = KLog.getLogFiles().single()
        val content = report.readText()

        assertEquals("md", report.extension)
        assertTrue(content.contains("| Timestamp (UTC) | Level |"))
        assertTrue(content.contains("Test suite"))
        assertTrue(content.contains("instrumented"))
        assertTrue(content.contains("```kotlin"))
        assertTrue(content.contains("IllegalArgumentException: Expected test exception"))
    }

    @Test
    fun runtimeConfigurationCreatesTextReport() {
        KLog.configure(KLogConfig(logFormat = LogFormat.TEXT))

        KLog.w("TextTest", "Plain text warning")

        val report = KLog.getLogFiles().single()
        val content = report.readText()

        assertEquals("txt", report.extension)
        assertTrue(content.contains("[WARN]"))
        assertTrue(content.contains("TextTest: Plain text warning"))
        assertFalse(content.contains("| Timestamp (UTC) |"))
    }

    @Test
    fun concurrentWritesDoNotLoseEntries() {
        val entryCount = 50
        val completed = CountDownLatch(entryCount)

        repeat(entryCount) { index ->
            Thread {
                try {
                    KLog.i("ConcurrencyTest", "Entry[$index]")
                } finally {
                    completed.countDown()
                }
            }.start()
        }

        assertTrue("Concurrent writes timed out", completed.await(10, TimeUnit.SECONDS))
        val content = KLog.getLogFiles().joinToString(separator = "\n") { it.readText() }

        repeat(entryCount) { index ->
            assertTrue("Entry[$index] was not persisted", content.contains("Entry[$index]"))
        }
    }

    @Test
    fun clearLogsRemovesManagedReports() {
        KLog.i("ClearTest", "This report will be deleted")
        assertTrue(KLog.getLogFiles().isNotEmpty())

        KLog.clearLogs()

        assertTrue(KLog.getLogFiles().isEmpty())
    }
}
