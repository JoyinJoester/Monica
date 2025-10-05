package takagi.ru.monica.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.*

/**
 * Clipboard utility for secure password copying
 */
class ClipboardUtils(private val context: Context) {
    
    private var clearClipboardJob: Job? = null
    
    /**
     * Copy text to clipboard with auto-clear after specified seconds
     */
    fun copyToClipboard(text: String, label: String = "Monica Password", autoClearSeconds: Int = 30) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        
        // Cancel previous clear job if exists
        clearClipboardJob?.cancel()
        
        // Schedule auto-clear
        clearClipboardJob = CoroutineScope(Dispatchers.Main).launch {
            delay(autoClearSeconds * 1000L)
            clearClipboard()
        }
    }
    
    /**
     * Clear clipboard immediately
     */
    private fun clearClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", "")
        clipboard.setPrimaryClip(clip)
    }
    
    /**
     * Cancel auto-clear job
     */
    fun cancelAutoClear() {
        clearClipboardJob?.cancel()
    }
}