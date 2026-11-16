# ZFS Vault 0.2

Android module: `:zfs`
Web app: `web/index.html`
Format specification: `docs/ZFS_FORMAT_V1.md`

Build: `gradle --no-daemon :zfs:assembleDebug`

Output: `zfs/build/outputs/apk/debug/zfs-debug.apk`

## Supported encrypted formats

- Text/data: `.ztxt`, `.zdoc`, `.zdata`, `.zlog`
- Media and files: `.zimg`, `.zvid`, `.zaud`, `.zpdf`, `.zarc`, `.zbin`

Import a file from the device, select a password, and create its encrypted ZFS container. On restore, image files can be previewed; other binary files can be exported and opened with a compatible app. Preserve the original filename before the ZFS extension (for example `scan.pdf.zpdf`) to retain its original extension on restore.

ZFSF v1 keeps its 38-byte header and AES-256-GCM/PBKDF2-HMAC-SHA256 encryption layout. Password loss means data loss. This prototype has not undergone an independent security audit.
