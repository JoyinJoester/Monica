package takagi.ru.monica.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CardScanResult(
    val cardNumber: String,
    val expiryMonth: String = "",
    val expiryYear: String = "",
    val cardholderName: String = "",
    val rawText: String = ""
)
