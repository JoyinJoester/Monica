//! Attachment ciphertext is streamed separately from the small causal replica records.
use super::*;
use base64::engine::general_purpose::STANDARD;
use chacha20poly1305::{
    aead::{Aead, KeyInit, Payload},
    ChaCha20Poly1305, Nonce,
};
use std::{
    fs::OpenOptions,
    io::{Read, Seek, SeekFrom, Write},
};

const MAGIC: &[u8] = b"MONICA-DEVICE-ASSETS-1\0";
const CHUNK: usize = 512 * 1024;
const HEADER: usize = 73;

fn header(file: &mut std::fs::File) -> Result<[u8; HEADER]> {
    let mut result = [0; HEADER];
    file.read_exact(&mut result).map_err(|_| "invalid_asset")?;
    let size = u32::from_le_bytes(result[68..72].try_into().unwrap()) as usize;
    if size > CHUNK || result[72] > 1 || !result[..64].iter().all(u8::is_ascii_hexdigit) {
        return Err("invalid_asset");
    }
    Ok(result)
}

pub(super) fn request(value: &Value) -> Result<Value> {
    let path = string(value, "path")?;
    match string(value, "operation")? {
        "asset_append" => {
            let id = string(value, "id")?;
            if id.len() != 64 || !id.bytes().all(|b| b.is_ascii_hexdigit()) {
                return Err("invalid_asset");
            }
            let plain = Zeroizing::new(
                STANDARD
                    .decode(string(value, "data")?)
                    .map_err(|_| "invalid_asset")?,
            );
            if plain.len() > CHUNK {
                return Err("asset_limit");
            }
            let key = peering::secret_key(string(value, "key")?)?;
            let index = value
                .get("index")
                .and_then(Value::as_u64)
                .filter(|n| *n <= u32::MAX as u64)
                .ok_or("invalid_asset")? as u32;
            let final_chunk = value
                .get("final")
                .and_then(Value::as_bool)
                .ok_or("invalid_asset")?;
            let mut h = [0u8; HEADER];
            h[..64].copy_from_slice(id.as_bytes());
            h[64..68].copy_from_slice(&index.to_le_bytes());
            h[68..72].copy_from_slice(&(plain.len() as u32).to_le_bytes());
            h[72] = u8::from(final_chunk);
            let mut nonce = [0u8; 12];
            rand::rngs::OsRng.fill_bytes(&mut nonce);
            let cipher = ChaCha20Poly1305::new_from_slice(&*key)
                .map_err(|_| "invalid_key")?
                .encrypt(
                    Nonce::from_slice(&nonce),
                    Payload {
                        msg: &plain,
                        aad: &h,
                    },
                )
                .map_err(|_| "invalid_asset")?;
            let mut file = match OpenOptions::new().write(true).create_new(true).open(path) {
                Ok(mut f) => {
                    f.write_all(MAGIC).map_err(|_| "storage_error")?;
                    f
                }
                Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {
                    OpenOptions::new()
                        .append(true)
                        .open(path)
                        .map_err(|_| "storage_error")?
                }
                Err(_) => return Err("storage_error"),
            };
            if file.metadata().map_err(|_| "storage_error")?.len()
                + (HEADER + 12 + cipher.len()) as u64
                > MAX_FILE
            {
                return Err("asset_limit");
            }
            file.write_all(&h)
                .and_then(|_| file.write_all(&nonce))
                .and_then(|_| file.write_all(&cipher))
                .map_err(|_| "storage_error")?;
            if final_chunk {
                file.sync_all().map_err(|_| "storage_error")?;
            }
            Ok(json!({}))
        }
        "asset_list" | "asset_read" => {
            let mut file = std::fs::File::open(path).map_err(|_| "asset_unavailable")?;
            let length = file.metadata().map_err(|_| "invalid_asset")?.len();
            if length > MAX_FILE {
                return Err("asset_limit");
            }
            let mut magic = vec![0u8; MAGIC.len()];
            file.read_exact(&mut magic).map_err(|_| "invalid_asset")?;
            if magic != MAGIC {
                return Err("invalid_asset");
            }
            if string(value, "operation")? == "asset_list" {
                let mut items = Vec::new();
                while file.stream_position().map_err(|_| "invalid_asset")? < length {
                    let offset = file.stream_position().map_err(|_| "invalid_asset")?;
                    let h = header(&mut file)?;
                    let size = u32::from_le_bytes(h[68..72].try_into().unwrap());
                    let end = offset + HEADER as u64 + 12 + u64::from(size) + 16;
                    if end > length || items.len() > 100_000 {
                        return Err("invalid_asset");
                    }
                    if u32::from_le_bytes(h[64..68].try_into().unwrap()) == 0 {
                        items.push(json!({"id":std::str::from_utf8(&h[..64]).map_err(|_| "invalid_asset")?, "offset":offset}));
                    }
                    file.seek(SeekFrom::Start(end))
                        .map_err(|_| "invalid_asset")?;
                }
                Ok(json!({"items":items}))
            } else {
                let offset = value
                    .get("offset")
                    .and_then(Value::as_u64)
                    .filter(|n| *n >= MAGIC.len() as u64 && *n < length)
                    .ok_or("invalid_asset")?;
                file.seek(SeekFrom::Start(offset))
                    .map_err(|_| "invalid_asset")?;
                let h = header(&mut file)?;
                let mut nonce = [0u8; 12];
                file.read_exact(&mut nonce).map_err(|_| "invalid_asset")?;
                let size = u32::from_le_bytes(h[68..72].try_into().unwrap()) as usize;
                let mut cipher = vec![0u8; size + 16];
                file.read_exact(&mut cipher).map_err(|_| "invalid_asset")?;
                let key = peering::secret_key(string(value, "key")?)?;
                let plain = Zeroizing::new(
                    ChaCha20Poly1305::new_from_slice(&*key)
                        .map_err(|_| "invalid_key")?
                        .decrypt(
                            Nonce::from_slice(&nonce),
                            Payload {
                                msg: &cipher,
                                aad: &h,
                            },
                        )
                        .map_err(|_| "invalid_asset")?,
                );
                Ok(
                    json!({"id":std::str::from_utf8(&h[..64]).map_err(|_| "invalid_asset")?, "index":u32::from_le_bytes(h[64..68].try_into().unwrap()),
                    "final":h[72] == 1, "data":STANDARD.encode(&*plain), "next":offset + HEADER as u64 + 12 + size as u64 + 16}),
                )
            }
        }
        _ => Err("invalid_request"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn assets_roundtrip_and_reject_wrong_keys_truncation_and_header_tampering() {
        let path = std::env::temp_dir().join(format!(
            "monica-assets-{}-{}.monica",
            std::process::id(),
            rand::random::<u64>()
        ));
        let key = URL_SAFE_NO_PAD.encode([7u8; 32]);
        let id = "a".repeat(64);
        request(&json!({"operation":"asset_append","path":path,"key":key,"id":id,"index":0,"final":true,"data":STANDARD.encode(b"synthetic attachment")})).unwrap();
        let offset = request(&json!({"operation":"asset_list","path":path})).unwrap()["items"][0]
            ["offset"]
            .as_u64()
            .unwrap();
        let read = json!({"operation":"asset_read","path":path,"key":key,"offset":offset});
        assert_eq!(
            STANDARD
                .decode(request(&read).unwrap()["data"].as_str().unwrap())
                .unwrap(),
            b"synthetic attachment"
        );
        let mut wrong = read.clone();
        wrong["key"] = json!(URL_SAFE_NO_PAD.encode([8u8; 32]));
        assert!(request(&wrong).is_err());
        let original = std::fs::read(&path).unwrap();
        let mut tampered = original.clone();
        tampered[MAGIC.len()] = b'b';
        std::fs::write(&path, tampered).unwrap();
        assert!(request(&read).is_err());
        std::fs::write(&path, &original[..original.len() - 1]).unwrap();
        assert!(request(&read).is_err());
        assert!(request(&json!({"operation":"asset_list","path":path})).is_err());
        std::fs::remove_file(&path).unwrap();
    }
}
