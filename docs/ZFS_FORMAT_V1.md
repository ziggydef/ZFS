# ZFSF Container Format v1

All files use an authenticated encrypted binary container. The extension is a hint only; the reader validates the magic bytes and authenticates the complete header as AES-GCM additional authenticated data.

## Header: 38 bytes, big-endian integers

| Offset | Length | Field |
|---:|---:|---|
| 0 | 4 | Magic: ASCII ZFSF |
| 4 | 1 | Version: 1 |
| 5 | 1 | Type: 1=ztxt, 2=zdoc, 3=zdata, 4=zlog |
| 6 | 4 | PBKDF2-HMAC-SHA256 iteration count |
| 10 | 16 | Random salt |
| 26 | 12 | Random AES-GCM nonce |

Bytes after offset 38 are AES-256-GCM ciphertext followed by the 16-byte authentication tag.

## Cryptography

- KDF: PBKDF2-HMAC-SHA256, 600,000 iterations, 256-bit output.
- Salt: 16 cryptographically random bytes per file.
- Encryption: AES-256-GCM, 96-bit nonce, 128-bit tag.
- AAD: complete 38-byte header.
- UI password minimum: 12 characters.
- Password and derived key are not stored in the file.

## Interoperability

Android and Web share the same byte layout, KDF, nonce and AAD.

## Security notes

- Renaming a ZFS extension to .txt does not decrypt its payload.
- This is an initial prototype, not an independently audited cryptographic product.
- Password loss means data loss unless a separate backup exists.
- The public format can be implemented by third parties; encryption, not obscurity, protects file contents.
