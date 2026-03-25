package takagi.ru.monica.domain.provider

data class PasswordCommandPolicy(
    val archiveProviderType: String,
    val shouldMarkPendingRemoteMutation: Boolean,
    val usesRemoteDeleteQueue: Boolean
)
