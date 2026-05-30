package takagi.ru.monica.bitwarden.mapper

import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.model.LOGIN_TYPE_SSH_KEY
import takagi.ru.monica.data.model.SshKeyData
import takagi.ru.monica.data.model.SshKeyDataCodec
import takagi.ru.monica.util.TotpDataResolver
import takagi.ru.monica.utils.PasswordWebsiteCodec
import java.util.Date

/**
 * 密码/登录凭据映射器
 * 
 * Monica PasswordEntry <-> Bitwarden Login (Type 1)
 * 
 * 这是最常用的映射器，处理标准的用户名/密码登录凭据。
 * 支持：URI、TOTP、自定义字段等。
 */
class LoginMapper : BitwardenMapper<PasswordEntry> {
    
    override fun toCreateRequest(item: PasswordEntry, folderId: String?): CipherCreateRequest {
        SshKeyDataCodec.decode(item.sshKeyData)?.let { ssh ->
            if (item.loginType.equals(LOGIN_TYPE_SSH_KEY, ignoreCase = true)) {
                // 使用 Type 1 + 自定义字段，兼容不支持 Type 5 的服务端（如 Vaultwarden）
                val sshFields = mutableListOf<CipherFieldApiData>()
                if (ssh.algorithm.isNotBlank()) sshFields.add(CipherFieldApiData(type = 0, name = "monica_ssh_algorithm", value = ssh.algorithm))
                if (ssh.keySize > 0) sshFields.add(CipherFieldApiData(type = 0, name = "monica_ssh_key_size", value = ssh.keySize.toString()))
                if (ssh.publicKeyOpenSsh.isNotBlank()) sshFields.add(CipherFieldApiData(type = 0, name = "monica_ssh_public_key", value = ssh.publicKeyOpenSsh))
                if (ssh.privateKeyOpenSsh.isNotBlank()) sshFields.add(CipherFieldApiData(type = 1, name = "monica_ssh_private_key", value = ssh.privateKeyOpenSsh))
                if (ssh.fingerprintSha256.isNotBlank()) sshFields.add(CipherFieldApiData(type = 0, name = "monica_ssh_fingerprint", value = ssh.fingerprintSha256))
                if (ssh.comment.isNotBlank()) sshFields.add(CipherFieldApiData(type = 0, name = "monica_ssh_comment", value = ssh.comment))
                if (ssh.format.isNotBlank()) sshFields.add(CipherFieldApiData(type = 0, name = "monica_ssh_format", value = ssh.format))
                sshFields.add(CipherFieldApiData(type = 0, name = "monica_login_type", value = LOGIN_TYPE_SSH_KEY))
                return CipherCreateRequest(
                    type = 1,
                    name = item.title,
                    notes = item.notes.takeIf { it.isNotBlank() },
                    folderId = folderId,
                    favorite = item.isFavorite,
                    login = CipherLoginApiData(
                        uris = emptyList(),
                        fido2Credentials = emptyList()
                    ),
                    fields = sshFields
                )
            }
        }

        val totpPayload = item.authenticatorKey.takeIf { it.isNotBlank() }?.let {
            TotpDataResolver.fromAuthenticatorKey(
                rawKey = it,
                fallbackIssuer = item.website,
                fallbackAccountName = item.username
            )?.let { resolved ->
                TotpDataResolver.toBitwardenPayload(item.title, resolved)
            } ?: it
        }

        return CipherCreateRequest(
            type = 1,  // Login
            name = item.title,
            notes = item.notes.takeIf { it.isNotBlank() },
            folderId = folderId,
            favorite = item.isFavorite,
            login = CipherLoginApiData(
                uris = buildUriList(item) ?: emptyList(),
                username = item.username.takeIf { it.isNotBlank() },
                password = item.password.takeIf { it.isNotBlank() },
                passwordRevisionDate = item.password.takeIf { it.isNotBlank() }?.let {
                    java.time.Instant.now().toString()
                },
                fido2Credentials = emptyList(),
                totp = totpPayload
            ),
            // 可选：添加自定义字段
            fields = buildCustomFields(item)
        )
    }
    
    override fun fromCipherResponse(cipher: CipherApiResponse, vaultId: Long): PasswordEntry {
        require(cipher.type == 1 || cipher.type == 5) { "LoginMapper only supports Login and SSH key ciphers" }

        if (cipher.type == 5) {
            return PasswordEntry(
                id = 0,
                title = cipher.name ?: "",
                website = "",
                username = "",
                password = "",
                notes = cipher.notes ?: "",
                createdAt = Date(),
                updatedAt = Date(),
                isFavorite = cipher.favorite == true,
                loginType = LOGIN_TYPE_SSH_KEY,
                sshKeyData = buildSshKeyData(cipher.sshKey),
                bitwardenVaultId = vaultId,
                bitwardenCipherId = cipher.id,
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                bitwardenCipherType = 5,
                bitwardenLocalModified = false
            )
        }
        
        val login = cipher.login
        
        // 从自定义字段中提取 Monica 特有数据
        val customFields = parseCustomFields(cipher.fields)
        
        return PasswordEntry(
            id = 0,
            title = cipher.name ?: "",
            website = extractWebsiteList(login?.uris),
            username = login?.username ?: "",
            password = login?.password ?: "",
            notes = cipher.notes ?: "",
            createdAt = Date(),
            updatedAt = Date(),
            isFavorite = cipher.favorite == true,
            authenticatorKey = login?.totp ?: "",
            // Monica 扩展字段从自定义字段恢复
            email = customFields["email"] ?: "",
            phone = customFields["phone"] ?: "",
            appPackageName = customFields["appPackageName"] ?: "",
            appName = customFields["appName"] ?: "",
            sshKeyData = buildSshKeyData(customFields),
            // Bitwarden 关联
            bitwardenVaultId = vaultId,
            bitwardenCipherId = cipher.id,
            bitwardenFolderId = cipher.folderId,
            bitwardenRevisionDate = cipher.revisionDate,
            bitwardenCipherType = 1,
            bitwardenLocalModified = false
        )
    }
    
    override fun hasDifference(item: PasswordEntry, cipher: CipherApiResponse): Boolean {
        if (cipher.type == 5) {
            val localSsh = SshKeyDataCodec.decode(item.sshKeyData)
            val remoteSsh = cipher.sshKey
            return !item.loginType.equals(LOGIN_TYPE_SSH_KEY, ignoreCase = true) ||
                    item.title != (cipher.name ?: "") ||
                    item.notes != (cipher.notes ?: "") ||
                    item.isFavorite != (cipher.favorite == true) ||
                    localSsh?.privateKeyOpenSsh.orEmpty() != remoteSsh?.privateKey.orEmpty() ||
                    localSsh?.publicKeyOpenSsh.orEmpty() != remoteSsh?.publicKey.orEmpty() ||
                    localSsh?.fingerprintSha256.orEmpty() != remoteSsh?.keyFingerprint.orEmpty()
        }

        if (cipher.type != 1) return true
        
        val login = cipher.login
        val localTotp = item.authenticatorKey.takeIf { it.isNotBlank() }?.let {
            TotpDataResolver.fromAuthenticatorKey(
                rawKey = it,
                fallbackIssuer = item.website,
                fallbackAccountName = item.username
            )
        }
        val remoteTotp = (login?.totp ?: "").takeIf { it.isNotBlank() }?.let {
            TotpDataResolver.fromAuthenticatorKey(
                rawKey = it,
                fallbackIssuer = extractWebsiteList(login?.uris),
                fallbackAccountName = login?.username ?: ""
            )
        }
        val sameTotp = when {
            localTotp == null && remoteTotp == null -> true
            localTotp != null && remoteTotp != null -> {
                TotpDataResolver.hasEquivalentOtpParameters(localTotp, remoteTotp)
            }
            else -> false
        }
        
        return item.title != cipher.name ||
                item.username != (login?.username ?: "") ||
                item.password != (login?.password ?: "") ||
                item.notes != (cipher.notes ?: "") ||
                item.isFavorite != (cipher.favorite == true) ||
                !sameTotp ||
                !matchUris(item, login?.uris)
    }
    
    override fun merge(
        local: PasswordEntry,
        remote: CipherApiResponse,
        preference: MergePreference
    ): PasswordEntry {
        return when (preference) {
            MergePreference.LOCAL -> local.copy(
                bitwardenRevisionDate = remote.revisionDate
            )
            MergePreference.REMOTE -> fromCipherResponse(remote, local.bitwardenVaultId ?: 0).copy(
                id = local.id,
                createdAt = local.createdAt,
                categoryId = local.categoryId,
                keepassDatabaseId = local.keepassDatabaseId,
                sortOrder = local.sortOrder,
                isGroupCover = local.isGroupCover,
                isDeleted = local.isDeleted,
                deletedAt = local.deletedAt,
                // 保留 Monica 扩展字段（如果远程没有）
                addressLine = local.addressLine,
                city = local.city,
                state = local.state,
                zipCode = local.zipCode,
                country = local.country,
                creditCardNumber = local.creditCardNumber,
                creditCardHolder = local.creditCardHolder,
                creditCardExpiry = local.creditCardExpiry,
                creditCardCVV = local.creditCardCVV
            )
            MergePreference.LATEST -> {
                val localTime = local.updatedAt.time
                val remoteTime = parseRevisionDate(remote.revisionDate)
                if (localTime > remoteTime) {
                    local
                } else {
                    merge(local, remote, MergePreference.REMOTE)
                }
            }
        }
    }
    
    /**
     * 构建 URI 列表
     */
    private fun buildUriList(item: PasswordEntry): List<CipherUriApiData>? {
        val uris = mutableListOf<CipherUriApiData>()
        
        // 网站 URL
        PasswordWebsiteCodec.parse(item.website)
            .filter { it.isNotBlank() }
            .map(PasswordWebsiteCodec::normalizeSingle)
            .distinct()
            .forEach { website ->
                uris.add(CipherUriApiData(uri = website))
            }
        
        // 应用包名 (Android 特有)
        if (item.appPackageName.isNotBlank()) {
            uris.add(CipherUriApiData(uri = "androidapp://${item.appPackageName}"))
        }
        
        return uris.takeIf { it.isNotEmpty() }
    }
    
    /**
     * 构建自定义字段（用于存储 Monica 特有数据）
     */
    private fun buildCustomFields(item: PasswordEntry): List<CipherFieldApiData>? {
        val fields = mutableListOf<CipherFieldApiData>()
        
        // 只添加非空的 Monica 扩展字段
        if (item.email.isNotBlank()) {
            fields.add(CipherFieldApiData(
                type = 0, // Text
                name = "email",
                value = item.email
            ))
        }
        if (item.phone.isNotBlank()) {
            fields.add(CipherFieldApiData(
                type = 0,
                name = "phone",
                value = item.phone
            ))
        }
        if (item.appPackageName.isNotBlank()) {
            fields.add(CipherFieldApiData(
                type = 0,
                name = "appPackageName",
                value = item.appPackageName
            ))
        }
        if (item.appName.isNotBlank()) {
            fields.add(CipherFieldApiData(
                type = 0,
                name = "appName",
                value = item.appName
            ))
        }
        SshKeyDataCodec.decode(item.sshKeyData)?.let { ssh ->
            if (item.loginType.equals(LOGIN_TYPE_SSH_KEY, ignoreCase = true)) {
                return@let
            }
            fields.add(CipherFieldApiData(type = 0, name = "monica_ssh_algorithm", value = ssh.algorithm))
            fields.add(CipherFieldApiData(type = 0, name = "monica_ssh_key_size", value = ssh.keySize.toString()))
            fields.add(CipherFieldApiData(type = 0, name = "monica_ssh_public_key", value = ssh.publicKeyOpenSsh))
            fields.add(CipherFieldApiData(type = 1, name = "monica_ssh_private_key", value = ssh.privateKeyOpenSsh))
            fields.add(CipherFieldApiData(type = 0, name = "monica_ssh_fingerprint", value = ssh.fingerprintSha256))
            fields.add(CipherFieldApiData(type = 0, name = "monica_ssh_comment", value = ssh.comment))
            fields.add(CipherFieldApiData(type = 0, name = "monica_ssh_format", value = ssh.format))
        }
        
        // 地址信息
        if (item.addressLine.isNotBlank() || item.city.isNotBlank()) {
            val address = listOfNotNull(
                item.addressLine.takeIf { it.isNotBlank() },
                item.city.takeIf { it.isNotBlank() },
                item.state.takeIf { it.isNotBlank() },
                item.zipCode.takeIf { it.isNotBlank() },
                item.country.takeIf { it.isNotBlank() }
            ).joinToString(", ")
            
            fields.add(CipherFieldApiData(
                type = 0,
                name = "address",
                value = address
            ))
        }
        
        return fields.takeIf { it.isNotEmpty() }
    }
    
    /**
     * 从自定义字段解析数据
     */
    private fun parseCustomFields(fields: List<CipherFieldApiData>?): Map<String, String> {
        if (fields.isNullOrEmpty()) return emptyMap()
        
        return fields.associate { field ->
            (field.name ?: "") to (field.value ?: "")
        }
    }
    
    /**
     * 从 URI 列表提取主域名
     */
    private fun extractWebsiteList(uris: List<CipherUriApiData>?): String {
        if (uris.isNullOrEmpty()) return ""

        return uris.asSequence()
            .mapNotNull { it.uri?.trim() }
            .filter { it.isNotBlank() && !it.startsWith("androidapp://", ignoreCase = true) }
            .map(PasswordWebsiteCodec::normalizeSingle)
            .distinct()
            .toList()
            .let(PasswordWebsiteCodec::encode)
    }
    
    /**
     * 检查 URI 是否匹配
     */
    private fun matchUris(item: PasswordEntry, remoteUris: List<CipherUriApiData>?): Boolean {
        val localWebsites = PasswordWebsiteCodec.parse(item.website)
            .filter { it.isNotBlank() }
            .map(::normalizeWebsiteKey)
            .filter { it.isNotBlank() }
            .distinct()
        val localPackage = item.appPackageName
        
        if (remoteUris.isNullOrEmpty()) {
            return localWebsites.isEmpty() && localPackage.isBlank()
        }
        
        val remoteWebsites = remoteUris.filter { !it.uri.isNullOrBlank() && !it.uri.startsWith("androidapp://") }
            .map { normalizeWebsiteKey(it.uri!!) }
            .filter { it.isNotBlank() }
            .distinct()
        val remotePackages = remoteUris.filter { it.uri?.startsWith("androidapp://") == true }
            .map { it.uri!!.removePrefix("androidapp://") }
        
        val websiteMatch = localWebsites == remoteWebsites
        val packageMatch = localPackage.isBlank() || remotePackages.contains(localPackage)
        
        return websiteMatch && packageMatch
    }

    private fun normalizeWebsiteKey(value: String): String {
        return PasswordWebsiteCodec.normalizeForKey(PasswordWebsiteCodec.normalizeSingle(value))
    }
    
    private fun parseRevisionDate(dateStr: String?): Long {
        if (dateStr == null) return 0
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            0
        }
    }

    private fun buildSshKeyData(sshKey: CipherSshKeyApiData?): String {
        if (sshKey == null) return ""
        return SshKeyDataCodec.encode(
            SshKeyData(
                algorithm = inferSshAlgorithm(sshKey.publicKey),
                publicKeyOpenSsh = sshKey.publicKey.orEmpty(),
                privateKeyOpenSsh = sshKey.privateKey.orEmpty(),
                fingerprintSha256 = sshKey.keyFingerprint.orEmpty(),
                format = SshKeyData.FORMAT_OPENSSH
            )
        )
    }

    private fun buildSshKeyData(fields: Map<String, String>): String {
        return SshKeyDataCodec.encode(
            SshKeyData(
                algorithm = fields["monica_ssh_algorithm"].orEmpty(),
                keySize = fields["monica_ssh_key_size"]?.toIntOrNull() ?: 0,
                publicKeyOpenSsh = fields["monica_ssh_public_key"].orEmpty(),
                privateKeyOpenSsh = fields["monica_ssh_private_key"].orEmpty(),
                fingerprintSha256 = fields["monica_ssh_fingerprint"].orEmpty(),
                comment = fields["monica_ssh_comment"].orEmpty(),
                format = fields["monica_ssh_format"].orEmpty().ifBlank { SshKeyData.FORMAT_OPENSSH }
            )
        )
    }

    private fun inferSshAlgorithm(publicKey: String?): String {
        return when {
            publicKey.orEmpty().startsWith("ssh-rsa") -> SshKeyData.ALGORITHM_RSA
            publicKey.orEmpty().startsWith("ssh-ed25519") -> SshKeyData.ALGORITHM_ED25519
            else -> ""
        }
    }
}
