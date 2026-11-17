import SwiftUI

struct AddDownloadSheet: View {
    @Environment(DownloadManager.self) private var downloads
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    let initialURL: String?

    private enum Phase: Equatable {
        case idle
        case probing
        case ready
        case failed(String)
    }

    @State private var urlText = ""
    @State private var phase: Phase = .idle
    @State private var probe: MediaProbe?
    @State private var spec: DownloadSpec?
    @State private var probeTask: Task<Void, Never>?
    @State private var detent: PresentationDetent = .medium
    @FocusState private var urlFocused: Bool

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    urlField

                    switch phase {
                    case .idle:
                        hint
                    case .probing:
                        probingCard
                    case .failed(let message):
                        failureCard(message)
                    case .ready:
                        if let probe, spec != nil {
                            PreviewCard(probe: probe, playlist: spec?.playlist ?? false)
                            options(for: probe)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 100)
                .animation(.smooth(duration: 0.35), value: phase)
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("Yeni indirme")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel("Kapat")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        startDownload()
                    } label: {
                        Image(systemName: "arrow.down")
                    }
                    .disabled(phase != .ready)
                    .accessibilityLabel("İndir")
                }
            }
            .safeAreaInset(edge: .bottom) {
                if phase == .ready {
                    Button(action: startDownload) {
                        Label(downloadButtonTitle, systemImage: "arrow.down.circle.fill")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.glassProminent)
                    .controlSize(.large)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 8)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
        .presentationDetents([.medium, .large], selection: $detent)
        .presentationDragIndicator(.visible)
        .onAppear {
            if let initialURL, !initialURL.isEmpty {
                urlText = initialURL
                probeCurrentURL()
            } else {
                urlFocused = true
            }
        }
        .onDisappear { probeTask?.cancel() }
    }

    // MARK: - URL entry

    private var urlField: some View {
        HStack(spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "link")
                    .foregroundStyle(.secondary)
                TextField("https://…", text: $urlText)
                    .keyboardType(.URL)
                    .textContentType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.go)
                    .focused($urlFocused)
                    .onSubmit(probeCurrentURL)
                if !urlText.isEmpty {
                    Button {
                        urlText = ""
                        phase = .idle
                        probe = nil
                        spec = nil
                        urlFocused = true
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Temizle")
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .glassEffect(.regular.interactive(), in: .capsule)

            PasteButton(payloadType: String.self) { strings in
                guard let first = strings.first else { return }
                Task { @MainActor in
                    urlText = first.trimmingCharacters(in: .whitespacesAndNewlines)
                    probeCurrentURL()
                }
            }
            .labelStyle(.iconOnly)
            .buttonBorderShape(.circle)
            .controlSize(.large)
        }
    }

    private var hint: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Bir video, oynatma listesi ya da kanal bağlantısı yapıştır.", systemImage: "sparkles")
                .font(.subheadline)
            Label("Paylaş menüsünden Kestirmeler'deki \"Parabolic ile indir\" eylemiyle de gönderebilirsin.", systemImage: "square.and.arrow.up")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .glassCard(cornerRadius: 24)
    }

    private var probingCard: some View {
        HStack(spacing: 14) {
            ProgressView()
                .controlSize(.regular)
            VStack(alignment: .leading, spacing: 3) {
                Text("Bağlantı inceleniyor…")
                    .font(.headline)
                Text("İlk açılışta yt-dlp'nin yüklenmesi birkaç saniye sürebilir.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .glassCard(cornerRadius: 24)
    }

    private func failureCard(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Bu bağlantı açılamadı", systemImage: "exclamationmark.triangle.fill")
                .font(.headline)
                .foregroundStyle(.orange)
            Text(message)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .textSelection(.enabled)
            Button("Tekrar dene", systemImage: "arrow.clockwise", action: probeCurrentURL)
                .buttonStyle(.glass)
        }
        .glassCard(cornerRadius: 24)
    }

    // MARK: - Options

    @ViewBuilder
    private func options(for probe: MediaProbe) -> some View {
        let specBinding = Binding<DownloadSpec>(
            get: { spec ?? settings.spec(url: urlText, probe: probe) },
            set: { spec = $0 }
        )
        let current = specBinding.wrappedValue

        if probe.isPlaylist || urlText.contains("list=") {
            GlassSection(title: "Oynatma listesi") {
                GlassRow(
                    symbol: "list.bullet.rectangle",
                    tint: .indigo,
                    title: "Listenin tamamını indir",
                    detail: probe.isPlaylist ? "\(probe.playlistCount ?? probe.entries.count) öğe" : "Yalnızca bu video seçili"
                ) {
                    Toggle("", isOn: Binding(
                        get: { current.playlist },
                        set: { newValue in
                            if newValue && !probe.isPlaylist {
                                startProbe(wholePlaylist: true)
                            } else {
                                specBinding.wrappedValue.playlist = newValue
                            }
                        }
                    ))
                    .labelsHidden()
                }
            }
        }

        GlassSection(title: "Biçim") {
            VStack(alignment: .leading, spacing: 14) {
                GlassChoiceBar(
                    options: probe.hasVideo ? MediaKind.allCases : [.audio],
                    selection: specBinding.kind,
                    label: { $0.label },
                    symbol: { $0.symbol }
                )

                if current.kind == .video {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Çözünürlük")
                            .font(.subheadline.weight(.semibold))
                        GlassChipRow(
                            options: VideoQuality.offered(forHeights: probe.heights),
                            selection: specBinding.quality,
                            label: { $0.label }
                        )
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Kodek")
                            .font(.subheadline.weight(.semibold))
                        GlassChoiceBar(
                            options: VideoCodec.allCases,
                            selection: specBinding.codec,
                            label: { $0.label },
                            symbol: nil
                        )
                        Text(current.codec.detail)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 8) {
                        GlassChoiceBar(
                            options: AudioFormat.allCases,
                            selection: specBinding.audioFormat,
                            label: { $0.label },
                            symbol: nil
                        )
                        Text(current.audioFormat.detail)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(10)
        }

        GlassSection(title: "Ekler") {
            GlassRow(symbol: "text.badge.star", tint: .blue, title: "Başlık ve sanatçı bilgisi") {
                Toggle("", isOn: specBinding.embedMetadata).labelsHidden()
            }
            GlassDivider()
            GlassRow(symbol: "photo", tint: .pink, title: "Kapak görseli") {
                Toggle("", isOn: specBinding.embedArtwork).labelsHidden()
            }
            if current.kind == .audio && current.embedArtwork {
                GlassDivider()
                GlassRow(symbol: "crop", tint: .orange, title: "Kapağı kare kırp", detail: "YouTube Music kapakları için") {
                    Toggle("", isOn: specBinding.cropArtwork).labelsHidden()
                }
            }
            if current.kind == .video {
                GlassDivider()
                GlassRow(symbol: "captions.bubble", tint: .teal, title: "Altyazılar", detail: "WebVTT dosyası olarak yanına") {
                    Toggle("", isOn: specBinding.subtitles).labelsHidden()
                }
                if current.subtitles {
                    TextField("Diller (ör. tr,en)", text: specBinding.subtitleLanguages)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .padding(.horizontal, 58)
                        .padding(.bottom, 10)
                }
            }
            if (probe.extractor ?? "").lowercased().hasPrefix("youtube") {
                GlassDivider()
                GlassRow(symbol: "scissors", tint: .green, title: "SponsorBlock", detail: "Sponsor ve tanıtım bölümlerini kes") {
                    Toggle("", isOn: specBinding.sponsorBlock).labelsHidden()
                }
            }
            if current.kind == .video {
                GlassDivider()
                GlassRow(symbol: "photo.on.rectangle", tint: .purple, title: "Fotoğraflar'a da kaydet") {
                    Toggle("", isOn: specBinding.saveToPhotos).labelsHidden()
                }
            }
        }
    }

    private var downloadButtonTitle: String {
        guard let spec else { return "İndir" }
        if spec.playlist, let count = probe?.playlistCount ?? probe?.entries.count, count > 0 {
            return "\(count) öğeyi indir"
        }
        return spec.kind == .audio ? "Sesi indir" : "Videoyu indir"
    }

    // MARK: - Actions

    private func probeCurrentURL() {
        startProbe(wholePlaylist: false)
    }

    private func startProbe(wholePlaylist: Bool) {
        let url = urlText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !url.isEmpty else { return }
        guard url.lowercased().hasPrefix("http") else {
            phase = .failed("Geçerli bir web bağlantısı gir (https://…).")
            return
        }
        urlFocused = false
        probeTask?.cancel()
        phase = .probing
        let previous = spec
        probeTask = Task { @MainActor in
            do {
                let result = try await YtdlpEngine.probe(url: url, wholePlaylist: wholePlaylist)
                guard !Task.isCancelled else { return }
                probe = result
                var newSpec = settings.spec(url: url, probe: result)
                if let previous {
                    // Keep choices the user already made when only the playlist scope changed.
                    newSpec.kind = result.hasVideo ? previous.kind : .audio
                    newSpec.quality = previous.quality
                    newSpec.codec = previous.codec
                    newSpec.audioFormat = previous.audioFormat
                    newSpec.embedMetadata = previous.embedMetadata
                    newSpec.embedArtwork = previous.embedArtwork
                    newSpec.cropArtwork = previous.cropArtwork
                    newSpec.subtitles = previous.subtitles
                    newSpec.subtitleLanguages = previous.subtitleLanguages
                    newSpec.sponsorBlock = previous.sponsorBlock
                    newSpec.saveToPhotos = previous.saveToPhotos
                }
                newSpec.playlist = result.isPlaylist
                spec = newSpec
                phase = .ready
                withAnimation(.smooth) { detent = .large }
            } catch is CancellationError {
                return
            } catch {
                guard !Task.isCancelled else { return }
                phase = .failed(error.localizedDescription)
            }
        }
    }

    private func startDownload() {
        guard var spec else { return }
        spec.url = urlText.trimmingCharacters(in: .whitespacesAndNewlines)
        downloads.enqueue(spec)
        dismiss()
    }
}

/// Thumbnail, title and source of the probed link.
private struct PreviewCard: View {
    let probe: MediaProbe
    let playlist: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            MediaThumbnail(urlString: probe.thumbnail, kind: probe.hasVideo ? .video : .audio, cornerRadius: 0)
                .aspectRatio(16 / 9, contentMode: .fit)
                .overlay(alignment: .bottomTrailing) {
                    if let duration = probe.duration, duration > 0, !playlist {
                        Text(Format.clock(duration))
                            .font(.caption.monospacedDigit().weight(.semibold))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .glassEffect(.regular, in: .capsule)
                            .padding(10)
                    }
                }
                .overlay(alignment: .topLeading) {
                    if playlist {
                        Label("\(probe.playlistCount ?? probe.entries.count) öğe", systemImage: "list.bullet")
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .glassEffect(.regular, in: .capsule)
                            .padding(10)
                    }
                }

            VStack(alignment: .leading, spacing: 4) {
                Text(probe.title)
                    .font(.headline)
                    .lineLimit(3)
                HStack(spacing: 6) {
                    if let uploader = probe.uploader {
                        Text(uploader)
                    }
                    if let extractor = probe.extractor {
                        Text("·")
                        Text(extractor)
                    }
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            }
            .padding(16)
        }
        .clipShape(.rect(cornerRadius: 28))
        .glassEffect(.regular, in: .rect(cornerRadius: 28))
    }
}
