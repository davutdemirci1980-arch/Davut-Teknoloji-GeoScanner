# GeoScanner — Native Android Uygulaması

Bu klasör, yüklediğiniz gerçek `com.geoscanner.app` APK'sından tersine mühendislik
(decompile) yoluyla yeniden oluşturulmuş, **derlenebilir bir Android Studio (Java)
projesidir**. Kayıp kaynak kodunuzun kurtarılmış/yeniden inşa edilmiş halidir;
orijinal Bluetooth donanım protokolü, dosya formatları ve 10 ekranın tamamı
korunmuştur.

> Not: `frontend/android/` klasöründeki Capacitor (WebView) tabanlı proje
> ayrı bir denemedir ve bu native projeyle ilgisi yoktur. Gerçek uygulamanız
> için bu `android-native/` klasörünü kullanın.

## İçerik

- **Paket adı:** `com.geoscanner.app`
- **10 ekran (Activity):** Splash, Ana Sayfa (dosya/cihaz seçimi), Tarama Kurulumu,
  Aktif Tarama, Tarama Önizleme (2D ısı haritası), Canlı 3D (Live3D), IsoSurface
  (Plotly.js 3B görselleştirme), 4D Voxler Pro (VTK.js hacim görselleştirme),
  Kayıtlar (dosya listesi/dışa aktarma), Ayarlar (dil seçimi)
- **Bluetooth:** Classic SPP + BLE (Nordic UART ve genel FFE0/FFF0 UUID'leri),
  özel "Zirve" ikili çerçeveleme protokolü (CRC-16 kontrolü ile)
- **Dosya formatları:** `.7esx` (AES şifreli), `.csv`, `.vtk`, `.grd`
  (Golden Software Surfer), `.dat`
- **Sinyal işleme:** 6 grid enterpolasyon modu, 14 2B filtre, 3B hacim
  oluşturma (derinlik katmanlı ekstrüzyon)
- **Diller:** Türkçe, İngilizce, Fransızca (`values-tr`, `values-fr`)

## Otomatik APK Derlemesi (GitHub Actions)

Bilgisayarınıza hiçbir şey kurmadan APK almak isterseniz: bu depoya her
`android-native/` içine push yapıldığında GitHub Actions otomatik olarak
bir debug APK derler.

1. GitHub'da depoyu açın → **Actions** sekmesi → **Android APK Build**
   iş akışını seçin.
2. Otomatik tetiklenmediyse sağ üstten **Run workflow** ile elle başlatın.
3. Derleme bitince (birkaç dakika sürer) çalışan iş akışının sayfasına girip
   en altta **Artifacts** bölümünden `geoscanner-debug-apk` dosyasını indirin
   — içinde kurulabilir `.apk` dosyası olacaktır.

Bu, imzasız bir **debug** APK'sıdır (test/kendi cihazınıza kurmak için
yeterlidir). Play Store'a yüklenecek imzalı bir **release** APK'sı için
aşağıdaki "Android Studio'da Açma ve Derleme" bölümündeki imzalama
adımlarını izlemeniz gerekir.

## Android Studio'da Açma ve Derleme

1. Android Studio'yu açın → **Open** → bu `android-native` klasörünü seçin.
2. Gradle senkronizasyonunun bitmesini bekleyin (internet bağlantısı gerekir,
   Android SDK ve bağımlılıklar Google/Maven Central üzerinden indirilir).
3. Üstteki cihaz/emülatör seçiciden bir cihaz seçip **Run ▶** tuşuna basın,
   ya da **Build → Build Bundle(s) / APK(s) → Build APK(s)** ile imzasız bir
   debug APK üretin.
4. İmzalı bir yayın (release) APK'sı için **Build → Generate Signed Bundle / APK**
   adımlarını izleyin ve kendi keystore'unuzu oluşturun/kullanın.

### Minimum gereksinimler

- Android Studio Hedgehog veya üzeri
- JDK 17
- Android SDK Platform 34 (compileSdk/targetSdk), minSdk 24
- `app/build.gradle` içindeki bağımlılıklar internet üzerinden otomatik
  indirilir (bu proje bu ortamda derlenmemiştir — ortamın ağ erişimi
  `dl.google.com`'u engellediği için Gradle bağımlılık indirmesi burada
  mümkün değildir; kendi bilgisayarınızda normal şekilde derlenecektir).

## Bilinen sınırlamalar / gözden geçirilmesi gerekenler

- Bazı decompile edilmiş layout metinlerinde emoji karakterleri kayboldu
  (`??` olarak göründüğü yerler); gerekirse `res/layout` ve `res/values`
  içinde elle düzeltebilirsiniz.
- BLE/Classic Bluetooth protokolü ve şifreleme anahtarı, orijinal APK'dan
  birebir çıkarılmıştır; gerçek donanımınızla test etmeniz önerilir.
- Bu proje, kaynak kodu olmayan kendi APK'nızdan (kullanıcı onayıyla)
  yeniden oluşturulmuştur.
