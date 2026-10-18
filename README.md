# RYZFinity Live (APK dengan server di dalam)

Satu app, tanpa hosting. Saat dibuka, server RYZFinity (Node.js) langsung jalan di HP, dashboard tampil otomatis, dan alert suara tetap bunyi di background saat kamu live TikTok.

## Isi repo
- `server.zip`  : server RYZFinity (tanpa node_modules). Ganti file ini kalau mau update server.
- `app/`        : project Android (WebView, service, jembatan native ke Node.js).
- `.github/workflows/main.yml` : build otomatis di GitHub Actions.

## Cara build
1. Di repo GitHub, hapus folder `app` lama (kalau ada), lalu upload isi zip ini (`app`, `server.zip`, `build.gradle`, `settings.gradle`, `gradle.properties`, `.gitignore`, `README.md`).
2. Isi file `.github/workflows/main.yml` dengan yang ada di zip ini (folder berawalan titik sering tidak ikut terupload, jadi tempel manual lewat editor GitHub).
3. Tab **Actions**, jalankan **Build APK**, tunggu sampai hijau, unduh artifact **RYZFinity-apk**.

## Pemakaian
1. Instal APK, buka. Izinkan notifikasi. Tunggu server menyala (pertama kali agak lama karena ekstrak file).
2. Di dashboard: isi username TikTok, pilih gaya overlay, klik Sambungkan.
3. Di tab Suara dan TTS pilih engine **Google** (suara bawaan browser tidak ada di WebView Android).
4. Buka TikTok dan mulai live. Alert, soundboard, dan roda berbunyi lewat speaker HP.
5. Menu titik tiga: Nyalakan/Matikan alert, Izinkan overlay (lebih stabil), Lihat log server, Keluar total.

Matikan penghemat baterai untuk app ini supaya Android tidak mematikan server di tengah live.

## Kalau ada masalah
Menu titik tiga, **Lihat log server**, lalu kirim isinya. Kalau build di GitHub gagal, kirim log langkah yang merah.
