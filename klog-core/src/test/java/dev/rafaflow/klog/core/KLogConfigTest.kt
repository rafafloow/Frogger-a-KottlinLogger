package dev.rafaflow.klog.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class KLogConfigTest {
    @Test
    fun `normalization clamps unsafe retention limits`() {
        val normalized = KLogConfig(
            maxLogAgeDays = -10,
            maxFolderSizeMb = 0,
        ).normalized()

        assertEquals(1, normalized.maxLogAgeDays)
        assertEquals(1, normalized.maxFolderSizeMb)
    }

    @Test
    fun `normalization snapshots and sanitizes custom metadata`() {
        val source = mutableMapOf(
            "" to "ignored",
            "Environment" to "staging",
        )

        val normalized = KLogConfig(customDeviceMetadata = source).normalized()
        source["Environment"] = "production"

        assertEquals(mapOf("Environment" to "staging"), normalized.customDeviceMetadata)
        assertNotSame(source, normalized.customDeviceMetadata)
    }
}
