# AppLexsa

Aplikasi pendamping Android 15 untuk meneruskan **satu** postingan WhatsApp Channels setelah pengguna menekan tombol melayang. Kotlin, UI XML, AccessibilityService, DataStore; tanpa root, server, login, atau modifikasi WhatsApp.

**Status: prototipe dengan selector eksperimental.** Build yang lulus belum membuktikan kompatibilitas WhatsApp. Ikuti [validasi perangkat](docs/VALIDATION.md) sebelum dipakai pada penjualan nyata.

## Pakai

1. Pasang APK uji dari artifact GitHub Actions **Android APK**.
2. Aktifkan layanan Accessibility sesuai panduan dalam aplikasi.
3. Di Pengaturan tujuan, ikat saluran dari layar WhatsApp dan konfirmasikan satu kontak yang dipilih manual di pemilih forward. Penyiapan tidak menekan kirim.
4. Batalkan pemilih penyiapan, aktifkan tombol, buka ujung saluran dan tampilkan postingan terakhir secara lengkap.
5. Tekan tombol melayang. AppLexsa memverifikasi target dan satu penerima, menekan kirim sekali, lalu menunggu kembali ke saluran. Tombol × menghentikan layanan otomatisasi.

Sumber di luar saluran terikat, identitas tak pasti, pilihan penerima ganda dan timeout akan menghentikan proses. Postingan sama diblokir lintas restart setelah percobaan kirim dicatat. “Perintah kirim dijalankan” bukan konfirmasi pesan sudah diterima. Kalibrasi hanya tersedia untuk ikon forward, bukan penerima/kirim.

Hanya WhatsApp biasa (`com.whatsapp`), bahasa Indonesia, satu profil, dan portrait yang menjadi sasaran v0.1. Nama saluran harus unik; dua saluran dengan judul sama belum dapat dibedakan secara andal. Fingerprint konservatif bisa memblokir postingan berbeda yang timestamp/teksnya identik; periksa manual sebelum reset.

## Build

JDK 17, SDK platform 35, Build Tools 35.0.0; Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20. Gradle Wrapper menyertakan checksum distribusi.

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Windows: gunakan `gradlew.bat`. SDK ditemukan melalui `ANDROID_HOME` atau `local.properties` yang tidak masuk Git.

Workflow berjalan pada push `main` / `feat/**`, pull request dan manual. Job build menghasilkan APK debug, SHA-256 dan laporan uji selama 14 hari; job emulator API 35 menjalankan smoke test. Unduh ZIP artifact, ekstrak, lalu pasang APK di dalamnya.

### Kunci uji tetap

Dengan GitHub CLI terautentikasi, jalankan di PowerShell:

```powershell
./tools/configure-test-signing.ps1
```

Script membuat keystore **khusus uji** beserta recovery lokal di `.signing/` (diabaikan Git) dan mengunggah empat repository secrets:

- `LEXSA_TEST_KEYSTORE_BASE64`
- `LEXSA_TEST_STORE_PASSWORD`
- `LEXSA_TEST_KEY_ALIAS`
- `LEXSA_TEST_KEY_PASSWORD`

Cadangkan `.signing/` secara privat. Script memakai ulang kunci yang ada, tidak memutarnya. Jangan gunakan kunci ini untuk produksi.

Build push/manual memakai secret jika tersedia; semua build pull request menggunakan kunci debug sementara dan tidak menerima signing secrets. Tanpa secrets, build push juga mengeluarkan peringatan dan memakai kunci sementara. APK dengan kunci berbeda memerlukan uninstall, yang menghapus data aplikasi. `BUILD.txt` pada artifact menyatakan jenis signing.

## Struktur

- `core`: mesin transaksi murni Kotlin, target terkunci, timeout, pencegahan kirim ganda.
- `automation`: adapter selector terpisah, snapshot Accessibility, overlay dan eksekusi yang memeriksa ulang layar.
- `data`: profil lokal dan diagnosis tanpa teks/deskripsi isi layar.

Tidak ada izin INTERNET, READ_CONTACTS, screen capture, atau SYSTEM_ALERT_WINDOW. Overlay menggunakan hak Accessibility. Ekspor laporan membuka share sheet hanya atas tindakan pengguna. Detail pengujian dan batas deteksi dijelaskan di [VALIDATION.md](docs/VALIDATION.md).
