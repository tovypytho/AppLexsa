# Validasi AppLexsa

## Status awal

Selector WhatsApp bersifat **eksperimental**, belum diverifikasi pada perangkat Android 15.
Video/screenshot menjadi referensi alur, bukan bukti bahwa node Accessibility tersedia.
HP yang terlihat lewat ADB saat perencanaan adalah vivo 1904 / Android 11; APK minSdk 35 tidak dipasang ke HP itu.

## Validasi selector pada HP Android 15

1. Unduh APK artifact `AppLexsa-debug-*` dari workflow **Android APK**, lalu pasang.
2. Aktifkan AppLexsa di Accessibility. Bila dibatasi, buka Info aplikasi → menu → Izinkan setelan terbatas (nama menu bergantung produsen), lalu coba lagi.
3. Di Diagnostik, pilih **Periksa struktur layar WhatsApp**, buka saluran uji dan tekan **Rekam diagnosis layar**.
4. Ekspor laporan. Ulangi untuk pemilih penerima tanpa seleksi, satu penerima dipilih, dan tampilan sesudah kirim manual. Simpan tiap ekspor terpisah karena laporan struktur terakhir diganti.
5. Periksa ID toolbar, list pesan, pembungkus postingan, forward, nama kontak, checkbox, jumlah pilihan dan kirim. Sesuaikan `WhatsAppSelectors` memakai bukti node nyata dan tambah fixture unit test yang sudah disamarkan.
6. Pengikatan/otomatisasi harus tetap diblokir ketika informasi wajib tidak terbaca. Jangan mengganti pemeriksaan penerima dengan koordinat.

Laporan hanya berisi struktur, panjang teks/deskripsi, status kontrol, kode, dan waktu. Profil tersimpan lokal dan tidak ikut ekspor. Laporan tidak memuat screenshot, teks pesan atau hash isi pesan.

## Penyiapan dan uji fungsional

- Ikat saluran dari layar saluran. Nama saluran harus unik di penggunaan Anda: Accessibility tidak menjamin akses ke ID saluran global.
- Buka forward secara manual dan pilih satu kontak. Gunakan tombol **Konfirmasi tujuan**, periksa dialog, lalu simpan. Ini tidak mengirim pesan. Kembali ke WhatsApp dan batalkan pemilih penyiapan sebelum mengaktifkan tombol.
- Aktifkan tombol dari Beranda. Buka saluran terikat, posisi paling bawah, postingan lengkap.
- Forward satu postingan ke akun uji. Periksa secara manual bahwa barang dan penerimanya benar.
- Coba tekanan ganda, postingan baru di tengah proses, kontak bernama sama, penerima ganda, scroll ke tengah saluran, postingan terpotong, internet terputus, lock screen, pindah aplikasi, rotasi dan ukuran tampilan berubah.
- Pastikan tidak ada pengiriman ulang otomatis sesudah timeout. Ketidakpastian sesudah klik kirim memerlukan pemeriksaan manual di WhatsApp sebelum reset blokir.
- Jalankan 20 percobaan dan ekspor diagnosis. Median/p95 mengukur saat mesin meminta klik kirim, bukan penerimaan server/penjual. Waktu kegagalan dan hasil UI dicatat terpisah.

## Kalibrasi cadangan

Kalibrasi hanya mengganti klik ikon forward. Postingan, ujung saluran, identitas penerima, satu pilihan dan tombol kirim tetap harus terdeteksi. Kalibrasi terikat koordinat layar dan hash struktur/bounds postingan; perubahan tata letak membuatnya tidak berlaku. Postingan tanpa timestamp teks yang terbaca ditolak.

Dua postingan dengan teks/timestamp/struktur yang tak dapat dibedakan sengaja berbagi fingerprint dan diblokir. View count, reaksi dan perubahan posisi baris tidak boleh membuat postingan lama dianggap baru. Reset manual tersedia setelah pengguna memeriksa pesan sebelumnya.

## Otomatis

`bash ./gradlew lintDebug testDebugUnitTest assembleDebug` memeriksa lint, mesin transaksi dan fixture selector. `connectedDebugAndroidTest` pada emulator API 35 memeriksa halaman, pemulihan tab dan manifest. Tidak ada uji CI yang mengirim pesan WhatsApp atau membuktikan selector pada HP nyata.
