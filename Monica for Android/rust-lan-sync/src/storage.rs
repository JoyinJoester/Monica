//! Publish completed encrypted artifacts without overwriting an existing destination.
use std::{
    fs::{File, OpenOptions},
    io::{self, Read},
    path::Path,
};

pub(super) fn publish_new(source: &Path, destination: &Path) -> io::Result<()> {
    if std::fs::hard_link(source, destination).is_ok() {
        return Ok(());
    }
    // Android app containers and some filesystems disallow hard links. The
    // destination belongs to one transfer and is consumed only after success.
    // create_new keeps the no-overwrite guarantee on both publication paths.
    copy_new(&mut File::open(source)?, destination)
}

fn copy_new(source: &mut impl Read, destination: &Path) -> io::Result<()> {
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut output = options.open(destination)?;
    let result = io::copy(source, &mut output).and_then(|_| output.sync_all());
    drop(output);
    if result.is_err() {
        // Only remove the file this call created, never an existing target.
        let _ = std::fs::remove_file(destination);
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{io::Cursor, path::PathBuf};

    struct Directory(PathBuf);
    impl Directory {
        fn new() -> Self {
            let root = std::env::temp_dir().join(format!(
                "monica-storage-test-{}",
                crate::peering::random_id()
            ));
            std::fs::create_dir(&root).unwrap();
            Self(root)
        }
    }
    impl Drop for Directory {
        fn drop(&mut self) {
            let _ = std::fs::remove_dir_all(&self.0);
        }
    }

    #[test]
    fn copy_fallback_preserves_existing_destination() {
        let root = Directory::new();
        let destination = root.0.join("changes.mdbxsync");
        std::fs::write(&destination, b"existing encrypted snapshot").unwrap();
        let result = copy_new(&mut Cursor::new(b"new encrypted snapshot"), &destination);
        assert_eq!(result.unwrap_err().kind(), io::ErrorKind::AlreadyExists);
        assert_eq!(
            std::fs::read(destination).unwrap(),
            b"existing encrypted snapshot"
        );
    }

    #[test]
    fn copy_fallback_removes_partial_files_and_allows_retry() {
        struct FailedRead;
        impl Read for FailedRead {
            fn read(&mut self, _: &mut [u8]) -> io::Result<usize> {
                Err(io::Error::other("simulated read failure"))
            }
        }
        let root = Directory::new();
        let destination = root.0.join("changes.mdbxsync");
        let mut input = Cursor::new(b"partial encrypted snapshot").chain(FailedRead);
        assert!(copy_new(&mut input, &destination).is_err());
        assert!(!destination.exists());
        copy_new(
            &mut Cursor::new(b"complete encrypted snapshot"),
            &destination,
        )
        .unwrap();
        assert_eq!(
            std::fs::read(destination).unwrap(),
            b"complete encrypted snapshot"
        );
    }

    #[test]
    fn concurrent_copy_fallbacks_publish_one_complete_file() {
        let root = Directory::new();
        let destination = root.0.join("changes.mdbxsync");
        let barrier = std::sync::Arc::new(std::sync::Barrier::new(8));
        let workers: Vec<_> = (0..8u8)
            .map(|value| {
                let destination = destination.clone();
                let barrier = barrier.clone();
                std::thread::spawn(move || {
                    barrier.wait();
                    let result = copy_new(&mut Cursor::new(vec![value; 16_384]), &destination);
                    if let Err(error) = &result {
                        assert_eq!(error.kind(), io::ErrorKind::AlreadyExists);
                    }
                    (value, result.is_ok())
                })
            })
            .collect();
        let winners: Vec<_> = workers
            .into_iter()
            .map(|worker| worker.join().unwrap())
            .filter(|(_, success)| *success)
            .map(|(value, _)| value)
            .collect();
        assert_eq!(winners.len(), 1);
        assert_eq!(
            std::fs::read(destination).unwrap(),
            vec![winners[0]; 16_384]
        );
    }
}
