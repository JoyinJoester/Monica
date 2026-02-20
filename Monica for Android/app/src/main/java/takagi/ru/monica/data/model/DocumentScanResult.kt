package takagi.ru.monica.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DocumentScanResult(
    val documentNumber: String = "",
    val fullName: String = "",
    val issuedDate: String = "",
    val expiryDate: String = "",
    val nationality: String = "",
    val rawText: String = ""
)
