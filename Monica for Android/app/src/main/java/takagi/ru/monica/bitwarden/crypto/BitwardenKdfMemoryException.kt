package takagi.ru.monica.bitwarden.crypto

class BitwardenKdfMemoryException(
    val requestedMemoryMb: Int,
    val maxHeapMb: Long,
    val safeLimitMb: Long
) : IllegalStateException(
    "Bitwarden Argon2id KDF memory is too high for Monica's current Android JVM crypto engine: " +
        "requested=${requestedMemoryMb}MB, safeLimit=${safeLimitMb}MB, heap=${maxHeapMb}MB"
)
