package takagi.ru.monica.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton to hold the current context (Package Name, URL) for Validator Auto Match
 */
object ValidatorContextManager {
    
    data class ContextState(
        val packageName: String = "",
        val url: String = ""
    )

    private val _contextState = MutableStateFlow(ContextState())
    val contextState: StateFlow<ContextState> = _contextState.asStateFlow()

    fun updatePackageName(packageName: String) {
        _contextState.value = _contextState.value.copy(packageName = packageName)
    }

    fun updateUrl(url: String) {
        _contextState.value = _contextState.value.copy(url = url)
    }
    
    fun updateContext(packageName: String, url: String) {
        _contextState.value = ContextState(packageName, url)
    }
}
