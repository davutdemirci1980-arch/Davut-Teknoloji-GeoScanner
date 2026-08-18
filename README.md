# Davut Teknoloji GeoScanner

Tam voksel tabanlı, çoklu sensörlü, yapay zeka destekli **yer altı görüntüleme simülasyon laboratuvarı**. Uygulama, gerçek bir sahaya çıkmadan; sentetik bir yeraltı sahnesi (jeolojik katmanlar + gömülü nesneler) üretir, bu sahneyi 8 farklı jeofizik sensör tipiyle "tarar", sensör verilerini yapay zeka ile birleştirip (fusion) anomali kümelerini tespit eder ve nesne türünü tahmin eder.

## Mimari

```
backend/   FastAPI + NumPy/SciPy simülasyon ve analiz motoru
frontend/  React + TypeScript + Three.js (react-three-fiber) 3B arayüz
```

### Voksel Motoru (`backend/app/core`)
- `materials.py`: 12 malzeme için fiziksel özellik kataloğu (dielektrik geçirgenlik, özdirenç, yoğunluk, manyetik duyarlılık, sismik hız, ısıl iletkenlik).
- `voxel_engine.py`: `VoxelGrid` — 3 boyutlu (X, Y, Derinlik) malzeme ızgarası. Katmanlı jeoloji üretimi, küre/kutu/silindir gömme (boru, boşluk, duvar, vb.), voksel bazlı fiziksel özellik sorguları.

### Simülasyon Laboratuvarı (`backend/app/simulation`)
- `scenarios.py`: Rastgele/altyapı hattı/arkeoloji/sadece-jeoloji senaryo ön ayarları ile sentetik sahne üretir.
- `lab.py`: Bellek içi laboratuvar oturumları — senaryo oluştur, sensörleri çalıştır, analiz et.

### Sensörler (`backend/app/sensors`) — 8 sensör tipi
| Sensör | Fiziksel prensip |
|---|---|
| GPR (Yer Radarı) | Dielektrik geçirgenlik sınırlarından EM yansıma + iletkenlik kaynaklı sönümleme |
| Manyetometre | Manyetik duyarlılık kontrastı, derinlikle hızlı sönümlenen alan anomalisi |
| ERT (Özdirenç Tomografisi) | Özdirenç kontrastı + derinlikle azalan yatay çözünürlük |
| Sismik Yansıma | Akustik empedans (yoğunluk × hız) sınır yansımaları |
| EMI (Elektromanyetik İndüksiyon) | Görünür iletkenlik, derinliğe göre tepe yapan tepki fonksiyonu |
| Termal / Kızılötesi | Sığ ısıl iletkenlik kontrastının yüzeye yansıması |
| Mikrogravite | Yoğunluk kontrastının yerçekimi alanına etkisi (derin, geniş dalga boylu) |
| LIDAR (Mikro-topografya) | Sığ boşluk/dolgu kaynaklı yüzey mikro-çökmeleri |

Her sensör, voksel ızgarası üzerinde **(nx, ny, nz) boyutunda bir olasılık hacmi** üretir — bu da "tam vokselli" laboratuvarın temelidir.

### Yapay Zeka Katmanı (`backend/app/ai`)
- `fusion.py`: Sensör güvenilirliklerine göre ağırlıklı çoklu-sensör füzyonu, voksel bazlı "kaç sensör hemfikir" sayacı.
- `anomaly_detector.py`: Birleşik olasılık hacminde eşikleme + 26-bağlantılı bileşen etiketleme (kümeleme) ile anomali tespiti.
- `classifier.py`: Her kümenin çoklu-sensör "kanıt vektörünü", bilinen gömülü nesne türleri için referans imzalarla kosinüs benzerliği + softmax ile karşılaştıran açıklanabilir sınıflandırıcı.

### API (`backend/app/api/routes.py`)
- `POST /api/scenarios` — yeni sahne üret
- `GET /api/scenarios/{id}/voxels` — tam voksel ızgarası (jeoloji)
- `POST /api/scenarios/{id}/sensors/run` — seçili sensörleri çalıştır
- `GET /api/scenarios/{id}/sensors/{key}/volume` — bir sensörün 3B olasılık hacmi
- `POST /api/scenarios/{id}/analyze` — AI füzyon + anomali tespiti + sınıflandırma
- `GET /api/scenarios/{id}/analysis/fused_volume` — birleşik olasılık hacmi
- `GET /api/materials`, `GET /api/sensors` — kataloglar

### Frontend (`frontend/src`)
- `VoxelViewer3D.tsx`: `InstancedMesh` ile tüm voksel ızgarasını 3B render eder (jeoloji / sensör ısı hacmi / AI füzyon+anomali modları arasında geçiş).
- `ScenarioControls`, `SensorPanel`, `SensorHeatmap`, `AnalysisPanel`, `Legend`: laboratuvar kontrol paneli.

## Çalıştırma

### Backend
```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

Vite dev sunucusu `/api` isteklerini `http://localhost:8000`'e proxy'ler (bkz. `vite.config.ts`). Tarayıcıda `http://localhost:5173` adresini açın.

### Testler
```bash
cd backend && source .venv/bin/activate && python3 -m pytest
```

## Bağımsız (Sunucusuz) Sürüm

`frontend/src/sim/` altında, backend'deki voksel motoru + 8 sensör + AI füzyon/sınıflandırma katmanının birebir TypeScript portu bulunuyor. Bu sayede uygulama **tamamen tarayıcıda, hiçbir sunucu olmadan** da çalışabiliyor — kurulum gerektirmeyen, tek bir HTML dosyasına paketlenmiş bir sürüm.

```bash
cd frontend
npm install
npm run build:standalone
```

Çıktı: `frontend/dist-standalone/index.html` — tek bir dosya, tek istisna Google Fonts hariç hiçbir dış bağlantı yapmaz; çift tıklayıp doğrudan tarayıcıda açabilir ya da herhangi bir statik dosya barındırma servisine (GitHub Pages, Netlify, vb.) yükleyebilirsiniz.

Bu, `src/api/client.ts` içindeki `IS_STANDALONE` bayrağı (`VITE_STANDALONE` ortam değişkeni) ile kontrol edilir; normal `npm run build` backend'e bağlı kalmaya devam eder, hiçbir davranış değişmez.

## Android (APK) Olarak Derleme

`frontend/android` klasöründe Capacitor ile hazırlanmış tam bir Android Studio projesi bulunuyor. Bu, web arayüzünü bir Android uygulaması olarak paketler. **Uygulama hâlâ ayrı bir backend'e ihtiyaç duyar** — telefon, backend'in çalıştığı adrese ağ üzerinden erişebilmelidir (aynı Wi-Fi'deki bilgisayarınız, ya da internete açık bir sunucu).

> Not: Bu proje bulut oturumunda gerçek bir `.apk` dosyası **üretilemedi** — Android SDK ve Gradle eklentisi Google'ın `dl.google.com` sunucusundan indiriliyor ve bu adres bu ortamda ağ politikası gereği engelli. Aşağıdaki adımlarla kendi bilgisayarınızda (normal internet erişimiyle) 5 dakikada APK üretebilirsiniz.

### Adımlar

1. **Android Studio'yu kurun**: https://developer.android.com/studio (SDK'yı otomatik kurar).
2. Backend'i bilgisayarınızda çalıştırın (yukarıdaki "Backend" bölümü) ve bilgisayarınızın **yerel ağ IP adresini** öğrenin (örn. `ipconfig` / `ifconfig` ile, genelde `192.168.x.x`).
3. Frontend'i derleyip Android projesine kopyalayın:
   ```bash
   cd frontend
   npm install
   npm run build
   npx cap sync android
   ```
4. Android Studio'da `frontend/android` klasörünü açın, Gradle senkronizasyonunun bitmesini bekleyin.
5. **Build > Build Bundle(s) / APK(s) > Build APK(s)** ile derleyin. Üretilen dosya `android/app/build/outputs/apk/debug/app-debug.apk` içinde olur; telefonunuza kopyalayıp kurabilirsiniz (bilinmeyen kaynaklardan yükleme izni gerekir).
6. Uygulamayı telefonda açın, sağ üstteki **"Sunucu Ayarı"**na dokunup backend adresini girin: `http://<bilgisayarınızın-IP'si>:8000/api` (telefon ve bilgisayar aynı Wi-Fi ağında olmalı).

## Kullanım Akışı

1. **Voksel Simülasyon Laboratuvarı** panelinden bir senaryo (karma / altyapı hatları / arkeoloji / sadece jeoloji) ve boyutlar seçip **"Yeni Senaryo Oluştur"** ile sentetik yeraltı sahnesini üretin.
2. **Çoklu Sensör Simülasyonu** panelinden istediğiniz sensörleri seçip **"Sensörleri Çalıştır"** ile her sensörün fiziksel tepkisini hesaplatın; 2B ısı haritalarını ve 3B olasılık hacmini inceleyin.
3. **Yapay Zeka Füzyon & Analiz** panelinden **"AI Analiz Çalıştır"** ile tüm sensör verilerini birleştirip anomalileri kümeleyin ve nesne türü tahminlerini (güven skoruyla) görün.
