package takagi.ru.monica.lansync

import org.json.JSONObject
import java.io.File

// Transport fixtures grant consent explicitly. Production receives must provide
// their own review callback; this overload exists only in the test source set.
internal suspend fun DevicePasswordSyncProvider.apply(directory: File, peer: JSONObject): Int =
    apply(directory, peer) { true }
