package takagi.ru.monica.autofill_ng

import android.app.Activity
import android.os.Bundle

/**
 * Inline suggestion slices require a resolvable PendingIntent even for direct-fill datasets.
 * Some ROMs do not behave well with an empty implicit intent, so keep a no-op explicit target.
 */
class AutofillInlinePlaceholderActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
