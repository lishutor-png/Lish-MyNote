# Lish MyNote

Aplikasi memo dan catatan Android modern dengan pencatatan otomatis tanggal dan jam, pengelompokan kategori kustom, brankas catatan tersembunyi dengan proteksi biometrik & PIN, pencadangan/pemulihan data JSON universal, serta dukungan penuh **Tema Terang & Gelap**.

---

## Fitur Utama

- ⏱️ **Pencatatan Otomatis Tanggal & Jam**: Setiap entri memo otomatis merekam waktu pembuatan dan modifikasi dalam format lokal Indonesia yang rapi.
- 🏷️ **Kategori Kustom Dinamis**: Pengelompokan catatan dengan label warna personal (Kerja, Pribadi, Ide, Belajar, dll.) serta filter instan.
- 🌓 **Tema Terang & Gelap (Light / Dark Mode)**: Pilihan tema fleksibel (*Terang*, *Gelap*, atau *Ikuti Sistem*) dengan kontras tinggi yang nyaman di mata untuk membaca memo panjang.
- 🔒 **Brankas Tersembunyi (Stealth Secret Vault)**: Akses rahasia tanpa petunjuk mencolok via ketukan 3 kali berturut-turut pada logo aplikasi di bilah atas, dilindungi sensor biometrik (sidik jari / kunci layar) dan PIN cadangan.
- 💾 **Pencadangan & Pemulihan Universal**: Dukungan ekspor dan impor berkas JSON dengan deteksi format fleksibel.
- ⚡ **Desain Modern Material 3 & Jetpack Compose**: Antarmuka bersih, responsif, dan mudah digunakan.

---

## Build di GitHub (GitHub Actions CI)

Proyek ini telah dikonfigurasi dengan workflow **GitHub Actions** di `.github/workflows/build.yml`.

### Cara Build & Download APK di GitHub:
1. Push kode ini ke repositori GitHub Anda.
2. Buka tab **Actions** di repositori GitHub.
3. Workflow **Build Android App** akan otomatis berjalan pada setiap *push* atau *pull request* ke branch `main`/`master`.
4. Anda juga dapat menjalankannya secara manual via tombol **Run workflow**.
5. Setelah build selesai, unduh berkas APK langsung pada bagian **Artifacts** (`LishMyNote-Debug-APK`).

---

## Build Lokal (Command Line / Android Studio)

### Prasyarat
- Java JDK 17 atau lebih baru
- Android SDK (API 34–36)

### Perintah Build
```bash
# Menjalankan unit test & Robolectric test
./gradlew :app:testDebugUnitTest

# Melakukan kompilasi dan pembuatan berkas APK Debug
./gradlew :app:assembleDebug
```
Berkas APK hasil build berada di:
`app/build/outputs/apk/debug/app-debug.apk`
