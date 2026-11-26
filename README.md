# Parabolic

[NickvisionApps/Parabolic](https://github.com/NickvisionApps/Parabolic)'in iş akışını
mobil cihazlara taşıyan bağımsız bir yt-dlp istemcisi. Bu depo iki uygulamayı
barındıran bir monorepo:

| Dizin | Platform | Teknoloji |
|---|---|---|
| [`android/app/`](android/app) | Android 12+ | Kotlin, Jetpack Compose, Material 3 Expressive |
| [`android/legacy/`](android/legacy) | Android 11 ve öncesi (7.0+) | Kotlin, Jetpack Compose, Material 3 |
| [`ios/`](ios) | iOS 26+ | Swift, SwiftUI, Liquid Glass |

## Sürümler

Her `v*` etiketi iki uygulamayı da GitHub Actions'ta derler ve APK'lar ile IPA'yı
eklenmiş bir GitHub Release yayınlar:

```bash
git tag v1.2.0
git push origin v1.2.0
```

`v1.3.0-beta.1` gibi tire içeren etiketler ön sürüm olarak yayınlanır. Sürüm
Actions sekmesindeki **Release** iş akışından elle de başlatılabilir.

| İş akışı | Ne zaman çalışır | Çıktı |
|---|---|---|
| `android.yml` | `android/` değişince, PR'larda | İki Android uygulaması için ABI başına imzalı APK |
| `ios.yml` | `ios/` değişince, PR'larda | İmzasız IPA |
| `release.yml` | `v*` etiketi | İkisini derler, Release yayınlar |

### Android imzalama anahtarı

Release APK'ları her sürümde aynı anahtarla imzalanmalı, yoksa kullanıcılar
güncelleme yapamaz. Anahtar depo gizlilerinden okunur:

| Gizli | İçerik |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 android/parabolic-release.jks` çıktısı |
| `ANDROID_KEYSTORE_PASSWORD` | Anahtar deposu parolası |
| `ANDROID_KEY_ALIAS` | Anahtar takma adı |
| `ANDROID_KEY_PASSWORD` | Anahtar parolası |

Bu gizliler yoksa `release.yml` bilerek başarısız olur; PR ve `main`
derlemeleri ise geçici bir hata ayıklama anahtarıyla devam eder.

## Android

Jetpack Compose ve Material 3 Expressive ile yazıldı; renkleri Material You
üzerinden duvar kağıdından alır.

- `youtubedl-android` paketindeki **QuickJS** yt-dlp'ye `--js-runtimes quickjs:`
  olarak geçiliyor; YouTube'un JavaScript challenge'ları böyle çözülüyor.
- yt-dlp, GitHub release'lerinden **çalışma zamanında güncelleniyor**; ayarlarda
  güncellemenin ilerlemesi indirme kartlarındaki çubukla gösteriliyor.
- İndirme uygulamanın özel dizinine yapılır, sonra MediaStore'a kopyalanır.
  Android 10 ve üstünde hiçbir depolama izni gerekmez.

### "Sign in to confirm you're not a bot"

YouTube bazı isteklere bu yanıtı verir; karar büyük ölçüde isteğin geldiği IP
adresine bağlıdır. Masaüstü Parabolic'te varsayılan olarak özel bir önlem yoktur:
yt-dlp'yi kalıcı önbelleğiyle çalıştırır ve gerektiğinde tarayıcı çerezlerini
kullanmayı önerir. Android uygulaması aynı yolu izler:

- yt-dlp kalıcı bir önbellekle (`--cache-dir`) çalışır; `youtubedl-android`
  aksi belirtilmedikçe `--no-cache-dir` geçtiği için önceden her istek
  YouTube'un oynatıcı kodunu yeniden indirip çözüyordu.
- Bu hata gelince istek bir kez `--force-ipv4` ile yinelenir; IPv6 adresleri
  daha sık işaretlenir. İşe yararsa o oturumda sonraki istekler de IPv4 kullanır.
- **Ayarlar › YouTube hesabı** uygulama içinde Google'a giriş yaptırır ve
  YouTube çerezlerini `--cookies` ile yt-dlp'ye verir. Bu, masaüstündeki
  "tarayıcı çerezleri" seçeneğinin karşılığıdır; çerezler yalnızca cihazda,
  uygulamanın özel dizininde durur.

```bash
cd android
./gradlew :app:assembleRelease
```

### Android 11 ve öncesi (`legacy`)

`android/legacy/` aynı uygulamanın eski Android sürümleri için ayrı bir APK'sıdır.
Arayüzü Google Saat ya da Telefon gibi sıradan bir Android uygulamasının düz
Material 3 görünümüdür: üst çubuk, alt gezinme çubuğu, "Yeni indirme" düğmesi,
liste satırları ve klasik ayar pencereleri. Yazı tipi sistemin kendi yazı tipidir;
renkler Android 12 ve üstünde duvar kağıdından, daha eskilerde uygulamanın sabit
paletinden gelir. Paletteki her renk rolü tek tek tanımlıdır ve MIUI gibi
arayüzlerin uygulamayı zorla koyulaştırması (force dark) kapalıdır.

Yalnızca arayüz bu dizinde yazıldı. İndirme motoru, kuyruk, ayarlar ve ön plan
servisi `android/app/` altındaki `core/` ve `service/` dosyalarından doğrudan
derlenir; orada yapılan bir düzeltme iki APK'ya da girer. `android/app/` bu
modül için hiç değiştirilmedi.

Uygulama kimliği `dev.eymistaken.parabolic.legacy` olduğundan normal sürümle aynı
telefona yan yana kurulabilir; ayarlar ve geçmiş ikisinde ayrı tutulur.

```bash
cd android
./gradlew :legacy:assembleRelease
```

## iOS

SwiftUI ile, Apple'ın iOS 26 tasarım dili **Liquid Glass** üzerine sıfırdan
yazıldı: `glassEffect`, `GlassEffectContainer` içinde birbirine akan seçim
çipleri, `tabViewBottomAccessory`'de süren indirme ve kaydırınca küçülen sekme
çubuğu. Arka plan sistemin düz rengidir: açık temada gri, koyu temada siyah.

iOS, uygulamaların alt süreç başlatmasına izin vermez; yani yt-dlp, ffmpeg ya
da Deno çalıştırılamaz. Uygulama bunu Apple'ın kendi çatılarıyla çözer:

| Katman | Ne yapıyor |
|---|---|
| `Engine/PythonShim.c` | Uygulamaya gömülü CPython'u (BeeWare `Python.xcframework`) başlatır, GIL'i yönetir |
| `python/parabolic_bridge.py` | yt-dlp'yi süreç içinde çalıştırır; her formatı ayrı dosya olarak indirir |
| `python/parabolic_jsc.py` | YouTube challenge'larını **JavaScriptCore** ile çözen yt-dlp eklentisi |
| `Media/MediaAssembler.swift` | Video ve sesi **AVFoundation** ile yeniden kodlamadan birleştirir, kapak ve etiket yazar, SponsorBlock bölümlerini keser |
| `Downloads/DownloadManager.swift` | Kuyruk, eşzamanlılık, ilerleme, **SwiftData** kitaplığı |
| `Downloads/BackgroundActivity.swift` | iOS 26 `BGContinuedProcessingTask` ile indirmeyi arka planda sürdürür |

ffmpeg olmadığından format seçimi AVFoundation'ın birleştirebildikleriyle
sınırlıdır: H.264 (her cihazda, YouTube'da 1080p'ye kadar) ya da AV1/HEVC
(4K'ya kadar) video ve AAC ses. Ses M4A olarak ya da kaynak biçiminde kaydedilir.

İndirilenler **Dosyalar › iPhone'umda › Parabolic** altında `Videolar` ve `Müzik`
klasörlerinde durur; istenirse videolar Fotoğraflar'a da eklenir. Kestirmeler'deki
**Parabolic ile indir** eylemi ve `parabolic://add?url=…` bağlantısı bir adresi
doğrudan uygulamaya gönderir.

### Yerelde derleme (macOS, Xcode 26)

```bash
brew install xcodegen
cd ios
./scripts/bootstrap.sh   # Python.xcframework, yt-dlp ve certifi'yi Vendor/ altına indirir
xcodegen generate
open Parabolic.xcodeproj
```

### Kurulum

Release'teki IPA imzasızdır. AltStore, SideStore, Sideloadly ya da TrollStore
ile kurulabilir; bu araçlar uygulamayı kendi hesabınla imzalar.

## Lisans

GPL-3.0. Android uygulaması `youtubedl-android` (GPL-3.0) kütüphanesine bağlı
olduğundan türev çalışma da GPL-3.0 altındadır. yt-dlp Unlicense, CPython PSF
lisanslıdır. Parabolic'in masaüstü sürümü MIT'tir ve buradaki indirme mantığı
oradaki yaklaşımdan türetilmiştir.
