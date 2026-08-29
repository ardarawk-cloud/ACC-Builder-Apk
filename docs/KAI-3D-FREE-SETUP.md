# KAI 3D FREE — Setup v1.0

Tujuan: generate 3D dari gambar untuk pemakaian pribadi tanpa kredit/API berbayar. APK hanya remote. Komputasi berjalan di GPU PC sendiri menggunakan Hunyuan3D 2.1.

## Arsitektur

HP (KAI 3D FREE APK) → HTTPS Quick Tunnel → KAI 3D Bridge :8090 → Hunyuan3D API :8081 → GPU → GLB

## Kebutuhan utama

- PC dengan NVIDIA GPU. Hunyuan3D 2.1 menyebut sekitar 10 GB VRAM untuk shape generation; texture generation membutuhkan jauh lebih besar.
- Python 3.10 direkomendasikan oleh upstream.
- Git, CUDA/PyTorch sesuai GPU.
- `cloudflared` untuk URL HTTPS gratis dari HP ke PC (opsional bila nanti APK diberi izin HTTP LAN langsung).

## 1. Install Hunyuan3D 2.1

Ikuti README resmi upstream untuk environment GPU. Baseline upstream saat dokumen ini dibuat:

```bash
git clone https://github.com/Tencent-Hunyuan/Hunyuan3D-2.1.git
cd Hunyuan3D-2.1
pip install torch==2.5.1 torchvision==0.20.1 torchaudio==2.5.1 --index-url https://download.pytorch.org/whl/cu124
pip install -r requirements.txt
```

Untuk texture/PBR, upstream juga membutuhkan komponen renderer tambahan. Untuk uji pertama KAI 3D FREE, gunakan **PBR Texture = OFF** agar kebutuhan GPU lebih ringan.

## 2. Jalankan API Hunyuan

Di folder `Hunyuan3D-2.1`:

```bash
python api_server.py --host 127.0.0.1 --port 8081 --low_vram_mode
```

Cek:

```text
http://127.0.0.1:8081/health
```

Harus mengembalikan status healthy.

## 3. Jalankan KAI 3D Bridge

Clone/download repo ACC-Builder-Apk pada PC, lalu dari root repo:

```bash
python tools/kai-3d-free/bridge.py
```

Bridge akan aktif di:

```text
http://127.0.0.1:8090
```

Cek:

```text
http://127.0.0.1:8090/health
```

`upstream` seharusnya `healthy`.

## 4. Buka akses HTTPS gratis untuk HP

Terminal baru:

```bash
cloudflared tunnel --url http://127.0.0.1:8090
```

Salin URL `https://....trycloudflare.com` yang muncul.

## 5. Pakai APK

1. Buka KAI 3D FREE.
2. Tempel URL Quick Tunnel ke **KAI 3D Bridge URL**.
3. Tekan **TEST** sampai Connected.
4. Pilih gambar objek.
5. Untuk uji awal: Remove Background ON, PBR Texture OFF.
6. Tekan **GENERATE 3D**.
7. Setelah `3D READY`, tekan **SAVE / SHARE GLB**.

## Catatan V1

- Image → 3D: aktif.
- Output: GLB.
- Job queue/polling: aktif.
- Tidak memakai kredit Meshy.
- Tidak membutuhkan AI API key.
- Roblox target faces dikirim sebagai parameter target, tetapi kualitas optimisasi akhir tetap bergantung pada kemampuan backend Hunyuan; auto-decimate Roblox khusus akan dibuat di tahap berikutnya.
- Text → 3D belum diaktifkan pada V1.

## Lisensi

KAI 3D FREE adalah client/bridge pribadi. Hunyuan3D 2.1 memiliki lisensi upstream sendiri; pengguna wajib mengikuti ketentuan lisensi upstream, khususnya jika penggunaan berubah dari personal menjadi komersial.
