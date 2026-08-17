# ACC AI Builder — GitHub Edition

Versi ini menghilangkan kebutuhan VPS Android.

## Arsitektur

**HP / GitHub Pages → request file di repository → GitHub Actions → AI generate/edit app → Capacitor → Gradle → APK → GitHub Release → Download APK ke HP**

GitHub Actions menjalankan proses berat. UI ACC AI Builder hanya menjadi remote control dari HP.

## Yang sudah tersedia

- UI mobile-first
- Project list
- Generate app lewat prompt
- Modify project lewat prompt berikutnya
- Preview hasil
- Read-only source viewer
- GitHub Actions build queue
- Android APK compilation
- APK stored as GitHub Release asset
- Download APK langsung dari UI
- GitHub Pages deployment workflow
- AI key hanya disimpan sebagai GitHub Actions Secret
- GitHub token user hanya disimpan di localStorage browser

## Setup pertama

### 1. Buat/upload repository

Upload seluruh isi folder ini ke sebuah repository GitHub.

Disarankan mulai dengan repository **private** jika source app tidak ingin publik.

### 2. Tambahkan GitHub Actions Secrets

Repository → **Settings → Secrets and variables → Actions → New repository secret**

Tambahkan:

- `AI_API_KEY`
- `AI_BASE_URL`
- `AI_MODEL`

AI endpoint harus kompatibel dengan OpenAI Chat Completions endpoint `/chat/completions`.

Jika ketiga secret ini belum diisi, workflow tetap bisa diuji tetapi menghasilkan aplikasi demo/mock.

### 3. Aktifkan GitHub Pages

Repository → **Settings → Pages**

Pada Build and deployment, gunakan **GitHub Actions**.

Workflow `.github/workflows/deploy-pages.yml` akan mempublikasikan folder `web/`.

### 4. Buat fine-grained Personal Access Token untuk HP

GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens.

Batasi token **hanya** untuk repository ACC AI Builder.

Izin minimum yang dibutuhkan UI:

- Repository permissions → **Contents: Read and write**
- Repository permissions → **Actions: Read**
- Repository permissions → **Metadata: Read** (otomatis)

Token ini dipakai UI untuk:
- membuat file request baru,
- membaca project,
- melihat status workflow,
- membaca GitHub Release,
- mengambil APK.

Jangan gunakan classic token dengan akses luas jika tidak diperlukan.

### 5. Buka GitHub Pages di HP

Buka URL Pages repository.

Tekan **GitHub Settings** lalu masukkan:

- GitHub username/owner
- nama repository
- branch `main`
- fine-grained token

### 6. Buat aplikasi

Contoh prompt:

> Buat aplikasi kasir warung untuk Android. Ada produk, stok, transaksi, total belanja, laporan harian, pencarian produk, dan semua data tersimpan offline di HP.

Tekan **Generate + Build APK**.

ACC AI Builder akan:

1. membuat `requests/<id>.json`
2. GitHub Actions membaca request
3. AI membuat `apps/<project>/index.html`
4. Capacitor membuat Android wrapper
5. Gradle compile `app-debug.apk`
6. project disimpan kembali ke repo
7. APK dibuat sebagai GitHub Release
8. UI mengaktifkan tombol **Download APK**

## Revisi aplikasi

Buka project lama lalu tulis:

> Tambahkan halaman laporan bulanan dan export CSV.

Tekan **Apply Change + Build APK**.

Workflow membaca versi app sebelumnya, mengirimkan source + instruksi perubahan ke AI, menyimpan versi baru, lalu membuat APK baru.

## Tentang APK

Workflow memakai `assembleDebug`. APK debug Android ditandatangani otomatis dan dapat di-install langsung di Android untuk penggunaan pribadi/testing.

Untuk Google Play Store, tambahkan pipeline **release signing + AAB** sebagai tahap berikutnya.

## Struktur repository

```text
.github/workflows/
  build-apk.yml
  deploy-pages.yml

web/
  index.html

scripts/
  ai_generate.py
  mark_build.py

apps/
  <project-id>/
    index.html
    project.json

requests/
  <request-id>.json
```

## Catatan keamanan

Versi ini dibuat untuk penggunaan personal/single-owner terlebih dahulu.

- AI API key tidak masuk ke browser.
- Fine-grained GitHub token tersimpan di browser perangkat.
- Batasi token hanya ke repo ACC AI Builder.
- Jangan memasukkan token GitHub ke prompt.
- Jangan menambahkan script eksternal yang tidak dipercaya ke UI builder.

## Tidak perlu VPS Android

GitHub-hosted runner menyediakan mesin build untuk workflow. Android SDK tersedia pada image runner Ubuntu 24.04, sehingga Android build dapat dijalankan di Actions tanpa Android Studio di HP atau PC.
