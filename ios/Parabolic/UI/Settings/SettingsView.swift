import SwiftUI

struct SettingsView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(DownloadManager.self) private var downloads
    @Environment(\.openURL) private var openURL

    var body: some View {
        @Bindable var settings = settings

        NavigationStack {
            ScrollView {
                VStack(spacing: 26) {
                    GlassSection(title: "Varsayılan biçim") {
                        VStack(alignment: .leading, spacing: 12) {
                            GlassChoiceBar(
                                options: MediaKind.allCases,
                                selection: $settings.defaultKind,
                                label: { $0.label },
                                symbol: { $0.symbol }
                            )
                        }
                        .padding(10)
                        GlassDivider()
                        GlassRow(symbol: "rectangle.on.rectangle", tint: .indigo, title: "Çözünürlük") {
                            Picker("Çözünürlük", selection: $settings.defaultQuality) {
                                ForEach(VideoQuality.allCases) { Text($0.label).tag($0) }
                            }
                            .labelsHidden()
                        }
                        GlassDivider()
                        GlassRow(symbol: "cpu", tint: .blue, title: "Video kodeği", detail: settings.defaultCodec.detail) {
                            Picker("Video kodeği", selection: $settings.defaultCodec) {
                                ForEach(VideoCodec.allCases) { Text($0.label).tag($0) }
                            }
                            .labelsHidden()
                        }
                        GlassDivider()
                        GlassRow(symbol: "music.note", tint: .pink, title: "Ses biçimi", detail: settings.defaultAudioFormat.detail) {
                            Picker("Ses biçimi", selection: $settings.defaultAudioFormat) {
                                ForEach(AudioFormat.allCases) { Text($0.label).tag($0) }
                            }
                            .labelsHidden()
                        }
                    }

                    GlassSection(title: "Ekler") {
                        GlassRow(symbol: "text.badge.star", tint: .blue, title: "Başlık ve sanatçı bilgisi") {
                            Toggle("", isOn: $settings.embedMetadata).labelsHidden()
                        }
                        GlassDivider()
                        GlassRow(symbol: "photo", tint: .pink, title: "Kapak görseli") {
                            Toggle("", isOn: $settings.embedArtwork).labelsHidden()
                        }
                        GlassDivider()
                        GlassRow(symbol: "crop", tint: .orange, title: "Ses kapağını kare kırp") {
                            Toggle("", isOn: $settings.cropArtwork).labelsHidden()
                        }
                        GlassDivider()
                        GlassRow(symbol: "captions.bubble", tint: .teal, title: "Altyazılar") {
                            Toggle("", isOn: $settings.subtitles).labelsHidden()
                        }
                        if settings.subtitles {
                            TextField("Diller (ör. tr,en)", text: $settings.subtitleLanguages)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .padding(.horizontal, 58)
                                .padding(.bottom, 10)
                        }
                        GlassDivider()
                        GlassRow(symbol: "scissors", tint: .green, title: "SponsorBlock", detail: "YouTube'da sponsor bölümlerini kes") {
                            Toggle("", isOn: $settings.sponsorBlock).labelsHidden()
                        }
                    }

                    GlassSection(
                        title: "Kaydetme",
                        footer: "İndirilenler Dosyalar › iPhone'umda › Parabolic altında, Videolar ve Müzik klasörlerinde durur."
                    ) {
                        GlassRow(symbol: "photo.on.rectangle", tint: .purple, title: "Videoları Fotoğraflar'a da kaydet") {
                            Toggle("", isOn: $settings.saveToPhotos).labelsHidden()
                        }
                        GlassDivider()
                        GlassRow(symbol: "bell.badge", tint: .red, title: "Bitince bildir") {
                            Toggle("", isOn: $settings.notifyOnFinish).labelsHidden()
                        }
                    }

                    GlassSection(title: "Performans") {
                        GlassRow(symbol: "square.stack.3d.down.right", tint: .cyan, title: "Aynı anda indirme") {
                            Stepper("\(settings.concurrentDownloads)", value: $settings.concurrentDownloads, in: 1...4)
                                .monospacedDigit()
                                .fixedSize()
                        }
                        GlassDivider()
                        GlassRow(symbol: "bolt.horizontal", tint: .yellow, title: "Paralel parça", detail: "DASH/HLS kaynaklarında hızı artırır") {
                            Stepper("\(settings.concurrentFragments)", value: $settings.concurrentFragments, in: 1...16)
                                .monospacedDigit()
                                .fixedSize()
                        }
                    }

                    engineSection(settings: settings)

                    GlassSection(title: "Görünüm") {
                        VStack {
                            GlassChoiceBar(
                                options: ThemeOption.allCases,
                                selection: $settings.theme,
                                label: { $0.label },
                                symbol: nil
                            )
                        }
                        .padding(10)
                    }

                    GlassSection(
                        title: "Hakkında",
                        footer: "Parabolic, yt-dlp'yi cihaz üzerinde, uygulamanın içine gömülü Python ile çalıştırır. Birleştirme ve etiketleme AVFoundation ile yapılır. GPL-3.0 lisanslıdır."
                    ) {
                        GlassRow(symbol: "app.badge", tint: .indigo, title: "Sürüm") {
                            Text(appVersion)
                                .foregroundStyle(.secondary)
                                .monospacedDigit()
                        }
                        GlassDivider()
                        Button {
                            openURL(URL(string: "https://github.com/Eymistaken/Parabolic")!)
                        } label: {
                            GlassRow(symbol: "chevron.left.forwardslash.chevron.right", tint: .gray, title: "Kaynak kodu") {
                                Image(systemName: "arrow.up.right")
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background { AmbientBackground() }
            .navigationTitle("Ayarlar")
        }
    }

    @ViewBuilder
    private func engineSection(settings: AppSettings) -> some View {
        @Bindable var settings = settings

        GlassSection(title: "yt-dlp", footer: downloads.updateMessage) {
            GlassRow(symbol: "shippingbox", tint: .orange, title: "Kullanılan sürüm", detail: engineDetail) {
                if downloads.isUpdating {
                    ProgressView()
                } else {
                    Button("Güncelle") {
                        Task { await downloads.updateEngine(force: true) }
                    }
                    .buttonStyle(.glassProminent)
                    .controlSize(.small)
                }
            }
            GlassDivider()
            GlassRow(symbol: "point.3.connected.trianglepath.dotted", tint: .mint, title: "Güncelleme kanalı") {
                Picker("Güncelleme kanalı", selection: $settings.updateChannel) {
                    ForEach(UpdateChannel.allCases) { Text($0.label).tag($0) }
                }
                .labelsHidden()
            }
            GlassDivider()
            GlassRow(symbol: "clock.arrow.circlepath", tint: .blue, title: "Günde bir kez kendiliğinden güncelle") {
                Toggle("", isOn: $settings.autoUpdate).labelsHidden()
            }
            if downloads.engineVersion?.updated != nil {
                GlassDivider()
                Button(role: .destructive) {
                    Task { await downloads.resetEngine() }
                } label: {
                    GlassRow(symbol: "arrow.uturn.backward", tint: .red, title: "Paketteki sürüme dön")
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var engineDetail: String {
        guard let version = downloads.engineVersion else { return "Yükleniyor…" }
        var parts: [String] = []
        if let effective = version.effective {
            parts.append(effective)
        }
        if version.source == "updated" || (version.loaded == nil && version.updated != nil) {
            parts.append("güncellenmiş")
        } else {
            parts.append("uygulamayla gelen")
        }
        if let python = version.python {
            parts.append("Python \(python)")
        }
        return parts.joined(separator: " · ")
    }

    private var appVersion: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(version) (\(build))"
    }
}
