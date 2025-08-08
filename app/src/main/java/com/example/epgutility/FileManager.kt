import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import java.io.File


object FileManager {

    sealed class CopyStatus {
        data class Progress(val fileName: String, val percent: Int) : CopyStatus()
        data class Completed(val fileName: String, val skipped: Boolean = false) : CopyStatus()
        data class Error(val fileName: String, val error: String) : CopyStatus()
        object Idle : CopyStatus()
    }

    private val _status = MutableStateFlow<CopyStatus>(CopyStatus.Idle)
    val status: StateFlow<CopyStatus> get() = _status

    suspend fun checkAndCopyIfNeeded(context: Context, sourcePath: String?): String? {
        if (sourcePath.isNullOrEmpty()) return null
        val source = File(sourcePath)
        if (!source.exists()) return null

        val dest = File(context.filesDir, "input/${source.name}")

        if (!dest.exists() || source.lastModified() > dest.lastModified()) {
            withContext(Dispatchers.IO) {
                try {
                    _status.value = CopyStatus.Progress(source.name, 0)

                    // Copy with progress
                    source.inputStream().use { input ->
                        dest.outputStream().use { output ->
                            val total = source.length()
                            val buffer = ByteArray(8192)
                            var copied: Long = 0
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                copied += read
                                val percent = (copied * 100 / total).toInt()
                                _status.value = CopyStatus.Progress(source.name, percent)
                            }
                        }
                    }

                    _status.value = CopyStatus.Completed(source.name)
                } catch (e: Exception) {
                    _status.value = CopyStatus.Error(source.name, e.message ?: "Unknown error")
                }
            }
        } else {
            // File already up-to-date -> emit skipped Completed event
            _status.value = CopyStatus.Completed(source.name, skipped = true)
        }

        return dest.absolutePath
    }
}
