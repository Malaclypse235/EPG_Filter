package com.example.epgutility

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

object FilterUtils {

    // Control object to manage pause/cancel
    class FilterControl {
        @Volatile var isPaused = false
        @Volatile var isCancelled = false

        fun pause() { isPaused = true }
        fun resume() { isPaused = false }
        fun cancel() { isCancelled = true }
    }

    data class FilterStats(
        var processed: Int = 0,   // Number of entries processed
        var removed: Int = 0      // Number of entries removed
    )

    /**
     * Unified filter function for M3U8 and EPG files.
     * Processes entire entries and supports pause/cancel.
     */
    suspend fun filterFileWithControl(
        inputFile: File,
        outputFile: File,
        control: FilterControl,
        removeNonLatin: Boolean,
        onProgress: (processed: Int, removed: Int) -> Unit
    ): FilterStats = withContext(Dispatchers.IO) {
        val stats = FilterStats()

        if (!inputFile.exists()) return@withContext stats

        BufferedReader(FileReader(inputFile)).use { reader ->
            PrintWriter(FileWriter(outputFile)).use { writer ->

                val entryBuffer = mutableListOf<String>()
                var inEntry = false

                fun writeEntryIfAllowed() {
                    if (entryBuffer.isEmpty()) return
                    stats.processed++

                    val entryText = entryBuffer.joinToString("\n")

                    val allow = if (removeNonLatin) {
                        containsOnlyLatin(entryText)
                    } else {
                        true
                    }

                    if (allow) {
                        entryBuffer.forEach { writer.println(it) }
                    } else {
                        stats.removed++
                    }
                    entryBuffer.clear()
                }

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (control.isCancelled) break

                    while (control.isPaused) {
                        Thread.sleep(100)
                        if (control.isCancelled) break
                    }
                    if (control.isCancelled) break

                    val currentLine = line ?: continue

                    if (currentLine.startsWith("#EXTINF", ignoreCase = true)) {
                        // Write previous entry before starting new one
                        if (inEntry) writeEntryIfAllowed()
                        inEntry = true
                        entryBuffer.add(currentLine)
                    } else {
                        if (inEntry) {
                            // Inside an entry
                            entryBuffer.add(currentLine)
                        } else {
                            // Before first #EXTINF: write immediately
                            writer.println(currentLine)
                        }
                    }

                    onProgress(stats.processed, stats.removed)
                }

                // Write the last entry at EOF
                if (inEntry && entryBuffer.isNotEmpty()) {
                    writeEntryIfAllowed()
                }
            }
        }

        return@withContext stats
    }

    // Check if text has only Latin characters
    private fun containsOnlyLatin(text: String): Boolean {
        for (char in text) {
            if (!char.isWhitespace() && !char.isISOControl() && !char.isLatinLetterOrDigit()) {
                return false
            }
        }
        return true
    }

    private fun Char.isLatinLetterOrDigit(): Boolean {
        return this.code < 128 // Basic Latin block only
    }

    // Convenience wrappers (for future EPG logic)
    suspend fun filterM3U8(
        inputFile: File,
        outputFile: File,
        control: FilterControl,
        removeNonLatin: Boolean,
        onProgress: (processed: Int, removed: Int) -> Unit
    ): FilterStats {
        return filterFileWithControl(inputFile, outputFile, control, removeNonLatin, onProgress)
    }

    suspend fun filterEPG(
        inputFile: File,
        outputFile: File,
        control: FilterControl,
        removeNonLatin: Boolean,
        onProgress: (processed: Int, removed: Int) -> Unit
    ): FilterStats {
        return filterFileWithControl(inputFile, outputFile, control, removeNonLatin, onProgress)
    }
}
