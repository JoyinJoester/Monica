use mdbx_crypto::keyring::Keyring;
use sha2::{Digest, Sha256};

use crate::error::{StorageError, StorageResult};

const COMMIT_INTEGRITY_DOMAIN: &[u8] = b"mdbx-commit-integrity-v1";

pub(crate) struct CommitIntegrityInput<'a> {
    pub commit_id: &'a str,
    pub device_id: &'a str,
    pub local_seq: u64,
    pub commit_kind: &'a str,
    pub change_scope: &'a str,
    pub changed_object_ids_ct: &'a [u8],
    pub vector_clock: &'a str,
    pub message_ct: Option<&'a [u8]>,
    pub created_at: &'a str,
    pub parents: &'a [String],
}

pub(crate) fn compute_commit_integrity_tag(
    keyring: Option<&Keyring>,
    input: &CommitIntegrityInput<'_>,
) -> StorageResult<Vec<u8>> {
    let local_seq = input.local_seq.to_le_bytes();
    let parent_count = (input.parents.len() as u64).to_le_bytes();
    let mut sorted_parents = input.parents.to_vec();
    sorted_parents.sort();

    let mut parts: Vec<&[u8]> = vec![
        COMMIT_INTEGRITY_DOMAIN,
        input.commit_id.as_bytes(),
        input.device_id.as_bytes(),
        &local_seq,
        input.commit_kind.as_bytes(),
        input.change_scope.as_bytes(),
        input.changed_object_ids_ct,
        input.vector_clock.as_bytes(),
    ];

    match input.message_ct {
        Some(message) => {
            parts.push(b"message:some");
            parts.push(message);
        }
        None => parts.push(b"message:none"),
    }

    parts.push(input.created_at.as_bytes());
    parts.push(&parent_count);
    for parent in &sorted_parents {
        parts.push(parent.as_bytes());
    }

    match keyring {
        Some(kr) => mdbx_crypto::integrity::hmac_sha256(&kr.integrity_subkey, &parts)
            .map_err(StorageError::Crypto),
        None => Ok(sha256_length_prefixed(&parts)),
    }
}

fn sha256_length_prefixed(parts: &[&[u8]]) -> Vec<u8> {
    let mut h = Sha256::new();
    for part in parts {
        h.update((part.len() as u64).to_le_bytes());
        h.update(part);
    }
    h.finalize().to_vec()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample<'a>(parents: &'a [String]) -> CommitIntegrityInput<'a> {
        CommitIntegrityInput {
            commit_id: "commit-1",
            device_id: "device-a",
            local_seq: 7,
            commit_kind: "change",
            change_scope: "entry",
            changed_object_ids_ct: br#"["entry-1"]"#,
            vector_clock: r#"{"device-a":7}"#,
            message_ct: None,
            created_at: "2026-05-22T00:00:00Z",
            parents,
        }
    }

    #[test]
    fn test_plain_integrity_tag_is_stable_and_non_placeholder() {
        let parents = vec!["p2".to_string(), "p1".to_string()];
        let tag1 = compute_commit_integrity_tag(None, &sample(&parents)).unwrap();
        let tag2 = compute_commit_integrity_tag(None, &sample(&parents)).unwrap();
        assert_eq!(tag1, tag2);
        assert_eq!(tag1.len(), 32);
        assert_ne!(tag1, vec![0]);
    }

    #[test]
    fn test_parent_order_is_canonical() {
        let a = vec!["p2".to_string(), "p1".to_string()];
        let b = vec!["p1".to_string(), "p2".to_string()];
        let tag_a = compute_commit_integrity_tag(None, &sample(&a)).unwrap();
        let tag_b = compute_commit_integrity_tag(None, &sample(&b)).unwrap();
        assert_eq!(tag_a, tag_b);
    }
}
