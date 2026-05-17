package takagi.ru.monica.bitwarden.crypto

object BitwardenArgon2MemoryGuard {
    private const val BYTES_PER_MB = 1024L * 1024L
    private const val MIN_APP_HEADROOM_MB = 96L
    private const val MAX_ARGON2_HEAP_FRACTION_DIVISOR = 2L

    fun requireCanRun(memoryMb: Int) {
        if (memoryMb <= 0) {
            throw IllegalArgumentException("Argon2 memory must be positive")
        }

        val runtime = Runtime.getRuntime()
        val maxHeapBytes = runtime.maxMemory()
        val usedHeapBytes = runtime.totalMemory() - runtime.freeMemory()
        val safeLimitMb = safeLimitMb(maxHeapBytes, usedHeapBytes)

        if (memoryMb.toLong() > safeLimitMb) {
            throw BitwardenKdfMemoryException(
                requestedMemoryMb = memoryMb,
                maxHeapMb = bytesToMb(maxHeapBytes),
                safeLimitMb = safeLimitMb
            )
        }
    }

    internal fun safeLimitMb(maxHeapBytes: Long, usedHeapBytes: Long): Long {
        val fractionLimitBytes = maxHeapBytes / MAX_ARGON2_HEAP_FRACTION_DIVISOR
        val availableLimitBytes = maxHeapBytes - usedHeapBytes - (MIN_APP_HEADROOM_MB * BYTES_PER_MB)
        val safeLimitBytes = minOf(fractionLimitBytes, availableLimitBytes).coerceAtLeast(0L)
        return bytesToMb(safeLimitBytes)
    }

    private fun bytesToMb(bytes: Long): Long = bytes / BYTES_PER_MB
}
