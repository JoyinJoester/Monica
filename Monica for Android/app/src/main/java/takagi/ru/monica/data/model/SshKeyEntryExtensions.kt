package takagi.ru.monica.data.model

import takagi.ru.monica.data.PasswordEntry

/**
 * `loginType` 取值常量（SSH 密钥条目）。
 *
 * 与 `"WIFI"`、`"SSO"`、`"PASSWORD"` 一样作为 [PasswordEntry.loginType] 的分派码。
 * 保持 UPPER_SNAKE_CASE 与其他类型一致；比较时统一 `equals(..., ignoreCase = true)`。
 */
const val LOGIN_TYPE_SSH_KEY: String = "SSH_KEY"

/**
 * 判断 PasswordEntry 是否为 SSH 密钥类型（通过 `loginType` 分派）。
 */
fun PasswordEntry.isSshKeyEntry(): Boolean =
    loginType.equals(LOGIN_TYPE_SSH_KEY, ignoreCase = true)

/**
 * 判断 PasswordEntry 是否为"纯 SSH 密钥"（仅包含 SSH 载荷、无密码/验证器载荷）。
 *
 * 与 [isSshKeyEntry] 不同：该判定仅凭 `sshKeyData` 内容成立，不依赖 `loginType`，
 * 主要用于历史 KeePass 导入等无法信赖 `loginType` 的场景。
 */
fun PasswordEntry.isDedicatedSshKeyEntry(): Boolean {
    val ssh = SshKeyDataCodec.decode(sshKeyData) ?: return false
    return ssh.publicKeyOpenSsh.isNotBlank() &&
        ssh.privateKeyOpenSsh.isNotBlank() &&
        password.isBlank() &&
        authenticatorKey.isBlank()
}
