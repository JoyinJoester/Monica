package takagi.ru.monica.lansync

import java.util.concurrent.atomic.AtomicInteger

/** Received changes wait until an open editor has saved or discarded its draft. */
internal object DeviceSyncEditGate {
    private val editors = AtomicInteger()
    val isEditing: Boolean get() = editors.get() > 0
    fun hold(): AutoCloseable {
        editors.incrementAndGet()
        return AutoCloseable { editors.decrementAndGet() }
    }
}
