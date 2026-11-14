package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SynthWaveDatabase
import com.example.data.model.LyricsSource
import com.example.data.model.formatDuration
import com.example.data.scanner.LyricsResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches SynthWave`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SynthWave", appName)
    }

    @Test
    fun `lrc parser sorts multi-timestamp lines chronologically`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = SynthWaveDatabase.getInstance(context)
        val resolver = LyricsResolver(context, db.synthWaveDao())

        val rawLrc = """
            [ar:Synth Artist]
            [00:45.50][00:12.25]Neon horizon
            [00:25.00]Pulse in the dark
        """.trimIndent()

        val parsed = resolver.parseLyricsText(rawLrc, LyricsSource.LRC_FILE)
        assertTrue(parsed.isSynced)
        assertEquals(3, parsed.lines.size)
        assertEquals(12_250L, parsed.lines[0].timestampMs)
        assertEquals("Neon horizon", parsed.lines[0].text)
        assertEquals(25_000L, parsed.lines[1].timestampMs)
        assertEquals("Pulse in the dark", parsed.lines[1].text)
        assertEquals(45_500L, parsed.lines[2].timestampMs)
    }

    @Test
    fun `plain text lyrics fallback is non-synced`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = SynthWaveDatabase.getInstance(context)
        val resolver = LyricsResolver(context, db.synthWaveDao())

        val parsed = resolver.parseLyricsText("First verse line\nSecond verse line", LyricsSource.TXT_FILE)
        assertFalse(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals("First verse line", parsed.lines[0].text)
    }

    @Test
    fun `formatDuration renders mm_ss and hh_mm_ss`() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("3:05", formatDuration(185_000L))
        assertEquals("1:02:09", formatDuration(3_729_000L))
    }
}
