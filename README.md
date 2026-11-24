# GENDEV Shorts

**GENDEV Shorts** adalah aplikasi Android khusus untuk menonton video pendek publik dari **YouTube Shorts** dan **TikTok** tanpa registrasi, tanpa login Google/TikTok, tanpa fitur sosial, dan tanpa penyimpanan riwayat tontonan.

---

## Fitur Utama

- **100% Tanpa Akun & Tanpa Login**: Langsung buka dan tonton. Tidak ada profil, komentar, like, follow, langganan, ataupun riwayat tontonan yang disimpan.
- **Dual Sumber Video Publik**:
  - **YouTube Shorts**: Integrasi multi-instansi **Piped API** & **Invidious API** + **YouTube oEmbed / IFrame Player** fallback. Mengenali tautan `/shorts/`, `youtu.be/`, dan `watch?v=` secara otomatis ke pemutar vertikal.
  - **TikTok**: Resolusi tautan pendek (`vt.tiktok.com`, `vm.tiktok.com`) tanpa cookie, **TikTok Official oEmbed**, ekstraksi stream publik non-auth (jika tersedia tanpa CAPTCHA/login), serta fallback ke **TikTok Embed v2**.
- **Sistem Ketahanan Multi-Source Fallback**:
  - Pemeriksaan kesehatan (*health check*) dan peringkat latensi antar-endpoint publik.
  - *Circuit breaker* cooldown otomatis (60 detik) untuk endpoint yang bermasalah.
  - Fallback mesin pemutar otomatis: **Media3 ExoPlayer** (utama, ringan RAM) → **Cookie-Free WebView** → pesan transparan + tombol coba lagi jika sumber membatasi akses (CAPTCHA / login wajib / DRM / region block).
- **UI/UX AMOLED Dark Futuristik**:
  - Latar hitam pekat `#000000` dengan aksen ungu elektrik `#8B5CF6` & tipografi *Space Grotesk* / *Plus Jakarta Sans* / *JetBrains Mono*.
  - `VerticalPager` swipe ke atas/bawah, mode fullscreen imersif, mute/unmute, dan indikator loading orbital.
  - Dukungan bahasa **Indonesia** & **Inggris** mengikuti bahasa sistem.
- **Ringan untuk Android 8.0+ (RAM 2 GB+)**:
  - Kontrol buffer Media3 hemat memori (`50 MB` batas cache sementara).
  - Penghentian pemutaran otomatis saat aplikasi masuk ke background (`ON_STOP` / `ON_PAUSE`).
  - Tombol **Hapus Cache Sementara** di panel Privasi.

---

## Struktur Direktori Utama

```text
├── .github/workflows/
│   └── android-apk.yml              # GitHub Actions CI untuk test & build APK otomatis
├── app/
│   ├── build.gradle.kts             # Konfigurasi Gradle modul aplikasi (com.gendev.shorts)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml  # Hanya INTERNET & ACCESS_NETWORK_STATE
│       │   ├── java/com/gendev/shorts/
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/
│       │   │   │   ├── cache/MediaCacheManager.kt
│       │   │   │   ├── network/EndpointHealthManager.kt
│       │   │   │   ├── parser/VideoUrlParser.kt
│       │   │   │   ├── remote/TikTokDataSource.kt
│       │   │   │   ├── remote/YouTubeShortsDataSource.kt
│       │   │   │   └── repository/ShortsRepository.kt
│       │   │   ├── domain/model/ShortsModels.kt
│       │   │   ├── player/ShortsPlayerController.kt
│       │   │   └── ui/
│       │   │       ├── components/
│       │   │       ├── screen/ShortsHomeScreen.kt
│       │   │       ├── theme/
│       │   │       └── viewmodel/ShortsViewModel.kt
│       │   └── res/
│       │       ├── font/            # Space Grotesk, Plus Jakarta Sans, JetBrains Mono
│       │       ├── values/          # String Bahasa Inggris (default)
│       │       └── values-in/       # String Bahasa Indonesia
│       └── test/java/com/gendev/shorts/
│           ├── EndpointHealthManagerTest.kt
│           ├── GendevShortsRobolectricTest.kt
│           └── VideoUrlParserTest.kt
└── gradle/libs.versions.toml
```

---

## Privasi & Batasan Akses

- **Tanpa pelacakan**: Aplikasi tidak menggunakan analytics SDK, advertising SDK, cookie login persisten, ataupun database riwayat tontonan.
- **Tanpa bypass ilegal**: Aplikasi tidak mengklaim dan tidak mencoba melewati CAPTCHA, login wajib, DRM, atau pembatasan server. Jika penyedia membatasi akses, pemutar menampilkan keterangan yang jelas.
- **Data koneksi pihak ketiga**: Saat memutar video, penyedia platform (YouTube / TikTok) atau instansi publik (Piped / Invidious) tetap menerima data koneksi jaringan standar (seperti alamat IP dan header HTTP) yang diperlukan untuk mengirim aliran video ke perangkat Anda.

---

## Petunjuk Build APK

### 1. Build Lokal (Command Line)

Pastikan Anda memiliki **JDK 17+**, **Android SDK (API 36)**, dan **Gradle 9.3.1** (atau Gradle wrapper jika diinisialisasi di proyek lokal):

1. Jalankan pengujian unit & Robolectric:
   ```bash
   gradle :app:testDebugUnitTest
   ```
2. Build Debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
   Output APK tersedia di:
   `app/build/outputs/apk/debug/app-debug.apk`
3. Build Release APK (membutuhkan keystore rilis):
   ```bash
   export KEYSTORE_PATH=/path/ke/my-upload-key.jks
   export STORE_PASSWORD=password_keystore
   export KEY_PASSWORD=password_alias
   gradle :app:assembleRelease
   ```
   Output APK tersedia di:
   `app/build/outputs/apk/release/app-release.apk`

### 2. Build Otomatis via GitHub Actions

1. Push kode ke branch `main` atau `master`, atau jalankan workflow secara manual dari tab **Actions → Android APK CI Build → Run workflow**.
2. Untuk mengaktifkan build Release APK bertanda tangan di GitHub Actions, tambahkan GitHub Repository Secrets berikut:
   - `KEYSTORE_BASE64`: isi file `.jks` yang di-encode Base64
   - `STORE_PASSWORD`: password keystore
   - `KEY_PASSWORD`: password key alias `upload`
3. Unduh hasil APK dari bagian **Artifacts** (`gendev-shorts-debug-apk` / `gendev-shorts-release-apk`).
