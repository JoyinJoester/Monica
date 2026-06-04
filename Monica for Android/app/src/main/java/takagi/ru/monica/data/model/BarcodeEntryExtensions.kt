package takagi.ru.monica.data.model

import takagi.ru.monica.data.PasswordEntry

const val LOGIN_TYPE_BARCODE: String = "BARCODE"

fun PasswordEntry.isBarcodeEntry(): Boolean =
    loginType.equals(LOGIN_TYPE_BARCODE, ignoreCase = true)

