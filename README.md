# RYZFinity Live (Android)

Aplikasi pembungkus (WebView) untuk dashboard RYZFinity. Fungsinya:
- Buka dashboard dari hosting kamu langsung dari HP (tanpa browser).
- **Alert background**: menjalankan `alerts.html` lewat service, jadi suara alert, soundboard, roda, dan TTS tetap bunyi saat kamu buka TikTok dan live.

## Cara build tanpa Android Studio (GitHub Actions)
1. Buat repo baru di GitHub (private juga boleh).
2. Ekstrak zip ini, lalu upload SEMUA isinya ke repo (folder `.github`, `app`, file `build.gradle`, dst harus ada di root repo). Kalau upload lewat web, seret isi foldernya, bukan zip-nya.
3. Buka tab **Actions**, pilih **Build APK**, klik **Run workflow** (atau otomatis jalan saat push).
4. Tunggu sekitar 3-6 menit, lalu buka run yang hijau dan unduh artifact **RYZFinity-apk** (isinya `app-debug.apk`).
5. Kirim APK ke HP, instal (izinkan "install dari sumber tidak dikenal").

## Cara build dengan Android Studio
Buka folder ini di Android Studio, tunggu sync, lalu menu **Build, Build Bundle(s)/APK(s), Build APK(s)**.

## Pemakaian
1. Buka app, isi alamat server (contoh `https://domainkamu.com`). Bisa diganti nanti lewat menu titik tiga.
2. Atur dashboard seperti biasa. Di tab **Suara & TTS**, pilih engine **Google** (suara bawaan browser tidak didukung WebView Android).
3. Menu titik tiga, **Mulai alert di background**, lalu buka TikTok dan mulai live.
4. Opsional tapi disarankan: menu **Izinkan overlay** supaya alert tidak dihentikan sistem.
5. Matikan penghemat baterai untuk app ini (Setelan, Aplikasi, RYZFinity Live, Baterai, Tanpa batasan).

## Batasan
- Live TikTok dari HP tidak bisa menerima audio dari app lain secara langsung. Suara keluar lewat speaker dan ditangkap mic, jadi pakai speaker HP, bukan earphone.
- Tombol "Unduh rekap (PNG)" tidak jalan di dalam app. Pakai browser HP untuk itu.
- APK ini versi debug (cukup untuk dipakai sendiri, bukan untuk Play Store).
