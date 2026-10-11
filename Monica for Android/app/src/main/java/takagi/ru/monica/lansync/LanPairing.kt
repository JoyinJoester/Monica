package takagi.ru.monica.lansync

import java.util.Base64

internal data class LanPeer(val name: String, val address: String, val port: Int)

/** UI parsing only; the Rust transport still authenticates the peer. */
internal data class LanPairingInvitation(val address: String, val port: Int, val code: String, val version: Int = 1) {
    val addresses: List<String> get() = address.split(',')
    val link: String get() = "monica-lan:$version:$address:$port:$code"
    override fun toString(): String = "LanPairingInvitation($address:$port)"

    companion object {
        fun parse(text: String): LanPairingInvitation? {
            val value = text.trim()
            if (value.length > 512) return null
            val parts = value.split(':')
            if (parts.size != 5 || parts[0] != "monica-lan" || parts[1] !in listOf("1", "2")) return null
            val addresses = parts[2].split(',')
            if (addresses.size !in 1..8 || addresses.distinct().size != addresses.size || (parts[1] == "1" && addresses.size != 1)) return null
            for (address in addresses) {
                val octets = address.split('.')
                if (octets.size != 4 || octets.any { it.isEmpty() || it.length > 3 ||
                        it.any { digit -> digit !in '0'..'9' } || (it.length > 1 && it[0] == '0') }) return null
                val ip = octets.map { it.toIntOrNull() ?: return null }
                if (ip.any { it !in 0..255 }) return null
                val local = ip[0] == 10 || ip[0] == 127 ||
                    (ip[0] == 172 && ip[1] in 16..31) ||
                    (ip[0] == 192 && ip[1] == 168) || (ip[0] == 169 && ip[1] == 254)
                if (!local) return null
            }
            if (parts[3].isEmpty() || parts[3].any { it !in '0'..'9' }) return null
            val port = parts[3].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val code = parts[4]
            if (code.length != 43 || code.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it != '-' && it != '_' }) return null
            val key = try { Base64.getUrlDecoder().decode(code) } catch (_: IllegalArgumentException) { return null }
            try {
                if (key.size != 32 || Base64.getUrlEncoder().withoutPadding().encodeToString(key) != code) return null
            } finally { key.fill(0) }
            return LanPairingInvitation(parts[2], port, code, parts[1].toInt())
        }
    }
}

/** Keep the endpoint, code and link together when a QR replaces a manual selection. */
internal data class LanPairingForm(
    val invitation: String = "",
    val pairingCode: String = "",
    val peer: LanPeer? = null,
    val autoFilled: Boolean = false
) {
    val connectionLink: String get() = invitation.trim().ifBlank {
        peer?.takeIf { pairingCode.isNotBlank() }
            ?.let { "monica-lan:1:${it.address}:${it.port}:${pairingCode.trim()}" }.orEmpty()
    }

    fun withInvitation(text: String, devices: List<LanPeer>): LanPairingForm {
        val parsed = LanPairingInvitation.parse(text)
            ?: return LanPairingForm(invitation = text)
        val device = devices.firstOrNull { it.address in parsed.addresses && it.port == parsed.port }
            ?: LanPeer(parsed.address, parsed.address, parsed.port)
        return LanPairingForm(parsed.link, parsed.code, device, autoFilled = true)
    }

    fun selectPeer(device: LanPeer): LanPairingForm = LanPairingForm(peer = device)
    fun withCode(code: String): LanPairingForm = if (autoFilled) this else copy(pairingCode = code)
    override fun toString(): String = "LanPairingForm(autoFilled=$autoFilled)"
}
