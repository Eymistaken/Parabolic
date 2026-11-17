# Parabolic Android

[NickvisionApps/Parabolic](https://github.com/NickvisionApps/Parabolic)'in iş akışını
Android'e taşıyan bağımsız bir yt-dlp istemcisi. Jetpack Compose ve Material 3
Expressive ile yazıldı; renkleri Material You üzerinden duvar kağıdından alır.

## Neden

Android'deki mevcut yt-dlp istemcileri bozulduğunda bunun iki nedeni oluyor:
içlerindeki yt-dlp eskimiş oluyor ve YouTube'un JavaScript challenge'larını
çözecek bir JS çalıştırıcısı bulunmuyor. Bu uygulama her ikisini de ele alıyor:

- `youtubedl-android 0.18.1` paketindeki **QuickJS** (`libqjs.so`) yt-dlp'ye
  `--js-runtimes quickjs:` olarak geçiliyor — masaüstü Parabolic'in Deno ile
  yaptığı işin Android karşılığı.
- yt-dlp, GitHub release'lerinden **çalışma zamanında güncelleniyor**. Varsayılan
  kanal `nightly` ve günde bir otomatik kontrol ediliyor.

## Nasıl çalışır

| Katman | Ne yapıyor |
|---|---|
| `core/YtdlpEngine` | yt-dlp argümanlarını kurar, `--dump-single-json` çıktısını çözer, indirmeyi yürütür |
| `core/DownloadRepository` | Kuyruk, eşzamanlılık, ilerleme durumu ve geçmiş |
| `core/MediaStoreSaver` | Bitmiş dosyayı `Movies/Parabolic` veya `Music/Parabolic` altına taşır |
| `service/DownloadService` | Ön plan servisi + wake lock, indirme sürerken süreç ölmesin diye |
| `ui/` | Compose arayüz: `MaterialExpressiveTheme`, `MotionScheme.expressive()` |

İndirme önce uygulamanın özel dizinine yapılır, sonra MediaStore'a kopyalanır.
Bu sayede Android 10 ve üstünde hiçbir depolama izni gerekmiyor.

## Derleme

```bash
./gradlew :app:assembleRelease
```

İmzalama bilgileri `keystore.properties` içinde; `parabolic-release.jks`
bu depo için üretilmiş kişisel bir anahtardır.

## Lisans

GPL-3.0. Uygulama `youtubedl-android` (GPL-3.0) kütüphanesine bağlı olduğundan
türev çalışma da GPL-3.0 altındadır. Parabolic'in masaüstü sürümü MIT'tir ve
buradaki indirme mantığı oradaki yaklaşımdan türetilmiştir.
