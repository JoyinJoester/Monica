package takagi.ru.monica.autofill

class DirectEntryModeResolver(
    private val cycleTtlMs: Long,
    private val maxSize: Int
) {
    enum class Mode {
        TRIGGER_ONLY,
        TRIGGER_AND_LAST_FILLED,
        LAST_FILLED_ONLY
    }

    data class Decision(
        val mode: Mode,
        val stage: Int,
        val reason: String
    )

    private data class CycleState(
        val stage: Int = 0,
        val requestOrdinal: Int = 0,
        val contextCount: Int = 0,
        val progressToken: String = "",
        val sessionMarker: String? = null,
        val fieldSignatureKey: String = "",
        val updatedAt: Long = 0L
    )

    private val cycleStates = mutableMapOf<String, CycleState>()

    fun clear() {
        cycleStates.clear()
    }

    fun resolve(
        cycleKey: String,
        hasLastFilled: Boolean,
        requestOrdinal: Int,
        contextCount: Int,
        progressToken: String,
        sessionMarker: String?,
        fieldSignatureKey: String,
        now: Long = System.currentTimeMillis()
    ): Decision {
        cleanup(now)

        if (!hasLastFilled) {
            cycleStates.remove(cycleKey)
            return Decision(
                mode = Mode.TRIGGER_ONLY,
                stage = 0,
                reason = "no_last_filled"
            )
        }

        val previous = cycleStates[cycleKey]
        if (previous == null) {
            cycleStates[cycleKey] = CycleState(
                stage = 0,
                requestOrdinal = requestOrdinal,
                contextCount = contextCount,
                progressToken = progressToken,
                sessionMarker = sessionMarker,
                fieldSignatureKey = fieldSignatureKey,
                updatedAt = now
            )
            return Decision(
                mode = Mode.TRIGGER_ONLY,
                stage = 0,
                reason = "new_cycle"
            )
        }

        val resetReason = when {
            requestOrdinal < previous.requestOrdinal -> "request_ordinal_rollback"
            contextCount < previous.contextCount -> "context_count_rollback"
            previous.sessionMarker != sessionMarker -> "session_marker_changed"
            previous.fieldSignatureKey != fieldSignatureKey -> "field_signature_changed"
            now - previous.updatedAt > cycleTtlMs -> "cycle_ttl_expired"
            else -> null
        }

        if (resetReason != null) {
            cycleStates[cycleKey] = CycleState(
                stage = 0,
                requestOrdinal = requestOrdinal,
                contextCount = contextCount,
                progressToken = progressToken,
                sessionMarker = sessionMarker,
                fieldSignatureKey = fieldSignatureKey,
                updatedAt = now
            )
            return Decision(
                mode = Mode.TRIGGER_ONLY,
                stage = 0,
                reason = resetReason
            )
        }

        val hasProgressed = progressToken != previous.progressToken
        val nextStage = if (!hasProgressed) {
            previous.stage
        } else {
            when (previous.stage) {
                0 -> 1
                else -> 2
            }
        }
        cycleStates[cycleKey] = previous.copy(
            stage = nextStage,
            requestOrdinal = requestOrdinal,
            contextCount = contextCount,
            progressToken = progressToken,
            sessionMarker = sessionMarker,
            fieldSignatureKey = fieldSignatureKey,
            updatedAt = now
        )
        val nextMode = when (nextStage) {
            0 -> Mode.TRIGGER_ONLY
            1 -> Mode.TRIGGER_AND_LAST_FILLED
            else -> Mode.LAST_FILLED_ONLY
        }
        val reason = if (!hasProgressed) {
            "duplicate_request"
        } else {
            "advance_from_${previous.stage}_to_$nextStage"
        }
        return Decision(
            mode = nextMode,
            stage = nextStage,
            reason = reason
        )
    }

    private fun cleanup(now: Long) {
        cycleStates.entries.removeAll { (_, state) ->
            now - state.updatedAt > cycleTtlMs
        }
        if (cycleStates.size <= maxSize) return
        val oldestKeys = cycleStates.entries
            .sortedBy { it.value.updatedAt }
            .take(cycleStates.size - maxSize)
            .map { it.key }
        oldestKeys.forEach { key -> cycleStates.remove(key) }
    }
}
