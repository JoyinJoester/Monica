package takagi.ru.monica.data.model

import takagi.ru.monica.data.PasswordEntry

fun PasswordEntry.isDedicatedSshKeyEntry(): Boolean {
    val ssh = SshKeyDataCodec.decode(sshKeyData) ?: return false
    return ssh.publicKeyOpenSsh.isNotBlank() &&
        ssh.privateKeyOpenSsh.isNotBlank() &&
        password.isBlank() &&
        authenticatorKey.isBlank()
}
