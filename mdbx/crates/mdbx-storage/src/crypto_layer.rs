use mdbx_crypto::error::CryptoResult;
use mdbx_crypto::keyring::Keyring;

/// 若已附加密钥环则加密，否则透明透传。
///
/// AAD 格式为 `"object_type:object_id:field_name"`，
/// 将每份密文绑定到其确切的列位置，防止密文替换攻击。
pub(crate) fn encrypt_field(
    keyring: Option<&Keyring>,
    subkey: &[u8],
    plaintext: &[u8],
    object_type: &str,
    object_id: &str,
    field_name: &str,
) -> CryptoResult<Vec<u8>> {
    match keyring {
        Some(_) => {
            let aad = build_aad(object_type, object_id, field_name);
            mdbx_crypto::aead::encrypt(subkey, plaintext, &aad)
        }
        None => Ok(plaintext.to_vec()),
    }
}

/// 若已附加密钥环则解密，否则透明透传（用于测试和明文模式）。
pub(crate) fn decrypt_field(
    keyring: Option<&Keyring>,
    subkey: &[u8],
    ciphertext: &[u8],
    object_type: &str,
    object_id: &str,
    field_name: &str,
) -> CryptoResult<Vec<u8>> {
    match keyring {
        Some(_) => {
            let aad = build_aad(object_type, object_id, field_name);
            mdbx_crypto::aead::decrypt(subkey, ciphertext, &aad)
        }
        None => Ok(ciphertext.to_vec()),
    }
}

fn build_aad(object_type: &str, object_id: &str, field_name: &str) -> Vec<u8> {
    format!("{}:{}:{}", object_type, object_id, field_name).into_bytes()
}
