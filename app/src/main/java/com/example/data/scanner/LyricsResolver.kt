package com.example.data.scanner

import android.content.Context
import android.net.Uri
import com.example.data.local.CustomLyricsEntity
import com.example.data.local.SynthWaveDao
import com.example.data.model.AudioTrack
import com.example.data.model.LyricLine
import com.example.data.model.LyricsData
import com.example.data.model.LyricsSource
import com.example.data.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class LyricsResolver(
    private val context: Context,
    private val dao: SynthWaveDao
) {
    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    suspend fun resolveLyrics(track: AudioTrack): LyricsData = withContext(Dispatchers.IO) {
        // 1. Check user-saved custom lyrics override in Room
        dao.getCustomLyricsOnce(track.id)?.let { saved ->
            if (saved.rawLyrics.isNotBlank()) {
                return@withContext parseLyricsText(saved.rawLyrics, LyricsSource.MANUAL)
            }
        }

        // 2. For local tracks, check sibling .lrc and .txt files in the exact same folder
        if (track.source == TrackSource.LOCAL) {
            val filePath = track.filePath
            if (!filePath.isNullOrBlank()) {
                val audioFile = File(filePath)
                val parent = audioFile.parentFile
                val baseName = audioFile.nameWithoutExtension
                if (parent != null && parent.exists() && baseName.isNotBlank()) {
                    val lrcFile = File(parent, "$baseName.lrc")
                    if (lrcFile.exists() && lrcFile.canRead()) {
                        val content = runCatching { lrcFile.readText(Charsets.UTF_8) }.getOrNull()
                        if (!content.isNullOrBlank()) {
                            return@withContext parseLyricsText(content, LyricsSource.LRC_FILE)
                        }
                    }
                    val txtFile = File(parent, "$baseName.txt")
                    if (txtFile.exists() && txtFile.canRead()) {
                        val content = runCatching { txtFile.readText(Charsets.UTF_8) }.getOrNull()
                        if (!content.isNullOrBlank()) {
                            return@withContext parseLyricsText(content, LyricsSource.TXT_FILE)
                        }
                    }
                }
            }

            // 3. Check embedded ID3v2 USLT lyrics tag via ContentResolver stream
            if (track.contentUri.isNotBlank()) {
                val uslt = extractEmbeddedUslt(Uri.parse(track.contentUri))
                if (!uslt.isNullOrBlank()) {
                    return@withContext parseLyricsText(uslt, LyricsSource.EMBEDDED_USLT)
                }
            }
        }

        LyricsData(source = LyricsSource.NONE)
    }

    suspend fun saveCustomLyrics(trackId: String, rawLyrics: String): LyricsData = withContext(Dispatchers.IO) {
        val trimmed = rawLyrics.trim()
        if (trimmed.isEmpty()) {
            dao.deleteCustomLyrics(trackId)
            return@withContext LyricsData(source = LyricsSource.NONE)
        }
        dao.upsertCustomLyrics(
            CustomLyricsEntity(
                trackId = trackId,
                rawLyrics = trimmed
            )
        )
        parseLyricsText(trimmed, LyricsSource.MANUAL)
    }

    suspend fun clearCustomLyrics(track: AudioTrack): LyricsData = withContext(Dispatchers.IO) {
        dao.deleteCustomLyrics(track.id)
        resolveLyrics(track)
    }

    fun readTextFromUri(uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        }.getOrNull()
    }

    fun parseLyricsText(rawText: String, source: LyricsSource): LyricsData {
        val rawLines = rawText.lines()
        val parsedTimed = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<LyricLine>()

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val matches = timestampRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val lyricBody = timestampRegex.replace(trimmed, "").trim()
                if (lyricBody.isNotEmpty()) {
                    for (match in matches) {
                        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                        val fractionRaw = match.groupValues[3]
                        val millis = when (fractionRaw.length) {
                            1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                            2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                            3 -> fractionRaw.toLongOrNull() ?: 0L
                            else -> 0L
                        }
                        val timestampMs = minutes * 60_000L + seconds * 1_000L + millis
                        parsedTimed += LyricLine(timestampMs = timestampMs, text = lyricBody)
                    }
                }
            } else {
                // Skip pure LRC metadata tags like [ar:...], [ti:...], [al:...], [by:...]
                if (!trimmed.matches(Regex("""^\[[a-zA-Z]+:.*]$"""))) {
                    plainLines += LyricLine(timestampMs = null, text = trimmed)
                }
            }
        }

        return if (parsedTimed.isNotEmpty()) {
            LyricsData(
                source = source,
                lines = parsedTimed.sortedBy { it.timestampMs ?: 0L },
                isSynced = true,
                rawText = rawText
            )
        } else {
            LyricsData(
                source = source,
                lines = plainLines,
                isSynced = false,
                rawText = rawText
            )
        }
    }

    private fun extractEmbeddedUslt(uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                parseId3v2Uslt(input)
            }
        }.getOrNull()
    }

    private fun parseId3v2Uslt(input: InputStream): String? {
        val header = ByteArray(10)
        if (input.read(header) != 10) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return null
        }

        val majorVersion = header[3].toInt() and 0xFF
        if (majorVersion < 3 || majorVersion > 4) return null

        val tagSize = syncSafeToInt(header, 6).coerceAtMost(2 * 1024 * 1024)
        val tagBytes = ByteArray(tagSize)
        var readTotal = 0
        while (readTotal < tagSize) {
            val read = input.read(tagBytes, readTotal, tagSize - readTotal)
            if (read <= 0) break
            readTotal += read
        }

        var offset = 0
        // Skip extended header if flag bit 6 is set
        val flags = header[5].toInt() and 0xFF
        if ((flags and 0x40) != 0 && offset + 4 <= readTotal) {
            val extSize = if (majorVersion == 4) syncSafeToInt(tagBytes, offset) else bigEndianToInt(tagBytes, offset)
            offset += extSize.coerceAtLeast(4)
        }

        while (offset + 10 <= readTotal) {
            if (tagBytes[offset] == 0.toByte()) break
            val frameId = String(tagBytes, offset, 4, StandardCharsets.ISO_8859_1)
            val frameSize = if (majorVersion == 4) {
                syncSafeToInt(tagBytes, offset + 4)
            } else {
                bigEndianToInt(tagBytes, offset + 4)
            }
            offset += 10
            if (frameSize <= 0 || offset + frameSize > readTotal) break

            if (frameId == "USLT") {
                return decodeUsltPayload(tagBytes, offset, frameSize)
            }
            offset += frameSize
        }
        return null
    }

    private fun decodeUsltPayload(data: ByteArray, start: Int, length: Int): String? {
        if (length < 5) return null
        val encoding = data[start].toInt() and 0xFF
        // Skip 1 byte encoding + 3 bytes language
        val contentStart = start + 4
        val end = start + length
        if (contentStart >= end) return null

        val isUtf16 = encoding == 1 || encoding == 2
        var descEnd = contentStart
        if (isUtf16) {
            while (descEnd + 1 < end) {
                if (data[descEnd] == 0.toByte() && data[descEnd + 1] == 0.toByte()) {
                    descEnd += 2
                    break
                }
                descEnd += 2
            }
        } else {
            while (descEnd < end) {
                if (data[descEnd] == 0.toByte()) {
                    descEnd += 1
                    break
                }
                descEnd += 1
            }
        }
        if (descEnd >= end) return null

        val charset: Charset = when (encoding) {
            0 -> StandardCharsets.ISO_8859_1
            1 -> StandardCharsets.UTF_16
            2 -> StandardCharsets.UTF_16BE
            3 -> StandardCharsets.UTF_8
            else -> StandardCharsets.UTF_8
        }
        return String(data, descEnd, end - descEnd, charset)
            .replace("\u0000", "")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun syncSafeToInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun bigEndianToInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }
}
