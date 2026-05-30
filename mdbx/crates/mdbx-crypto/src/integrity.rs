use hmac::{Hmac, Mac};
use sha2::Sha256;

use crate::error::{CryptoError, CryptoResult};

type HmacSha256 = Hmac<Sha256>;

/// Computes an HMAC-SHA-256 tag over length-delimited parts.
///
/// Length-prefixing keeps adjacent fields unambiguous, so callers can build
/// stable tags without inventing ad hoc separators.
pub fn hmac_sha256(key: &[u8], parts: &[&[u8]]) -> CryptoResult<Vec<u8>> {
    if key.len() != 32 {
        return Err(CryptoError::InvalidParameter(
            "HMAC key must be 32 bytes".to_string(),
        ));
    }

    let mut mac = HmacSha256::new_from_slice(key)
        .map_err(|e| CryptoError::InvalidParameter(format!("invalid HMAC key: {}", e)))?;
    for part in parts {
        mac.update(&(part.len() as u64).to_le_bytes());
        mac.update(part);
    }
    Ok(mac.finalize().into_bytes().to_vec())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hmac_sha256_is_deterministic() {
        let key = [7u8; 32];
        let a = hmac_sha256(&key, &[b"commit", b"field"]).unwrap();
        let b = hmac_sha256(&key, &[b"commit", b"field"]).unwrap();
        assert_eq!(a, b);
        assert_eq!(a.len(), 32);
    }

    #[test]
    fn test_length_prefix_prevents_ambiguity() {
        let key = [9u8; 32];
        let a = hmac_sha256(&key, &[b"ab", b"c"]).unwrap();
        let b = hmac_sha256(&key, &[b"a", b"bc"]).unwrap();
        assert_ne!(a, b);
    }

    #[test]
    fn test_invalid_key_rejected() {
        let err = hmac_sha256(b"short", &[b"x"]).unwrap_err();
        assert!(matches!(err, CryptoError::InvalidParameter(_)));
    }
}
