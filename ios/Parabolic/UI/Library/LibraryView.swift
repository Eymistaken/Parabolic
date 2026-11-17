import QuickLook
import SwiftData
import SwiftUI
import UIKit

struct LibraryView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(DownloadManager.self) private var downloads
    @Query(sort: \LibraryItem.completedAt, order: .reverse) private var items: [LibraryItem]

    @State private var query = ""
    @State private var filter: LibraryFilter = .all
    @State private var previewURL: URL?
    @State private var pendingDeletion: LibraryItem?

    private var visibleItems: [LibraryItem] {
        items.filter { item in
            let kindMatches = filter == .all || (filter == .video ? item.kind == .video : item.kind == .audio)
            guard kindMatches else { return false }
            guard !query.isEmpty else { return true }
            return item.title.localizedCaseInsensitiveContains(query)
                || (item.uploader ?? "").localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if items.isEmpty {
                    GlassEmptyState(
                        symbol: "square.stack",
                        title: "Kitaplık boş",
                        message: "Tamamlanan indirmeler burada ve Dosyalar uygulamasında, Parabolic klasöründe görünür."
                    ) {
                        EmptyView()
                    }
                    .padding(.top, 80)
                } else {
                    VStack(spacing: 16) {
                        GlassChoiceBar(
                            options: LibraryFilter.allCases,
                            selection: $filter,
                            label: { $0.label },
                            symbol: { $0.symbol }
                        )

                        if visibleItems.isEmpty {
                            Text("Eşleşen indirme yok.")
                                .foregroundStyle(.secondary)
                                .padding(.top, 40)
                        }

                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 158), spacing: 14)], spacing: 14) {
                            ForEach(visibleItems) { item in
                                LibraryTile(item: item) {
                                    previewURL = item.existingFiles.first
                                }
                                .contextMenu { menu(for: item) }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                    .animation(.smooth, value: filter)
                }
            }
            .background { AmbientBackground() }
            .navigationTitle("Kitaplık")
            .searchable(text: $query, prompt: "Kitaplıkta ara")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        openInFiles(AppPaths.documents)
                    } label: {
                        Label("Dosyalar'da aç", systemImage: "folder")
                    }
                }
            }
            .quickLookPreview($previewURL)
            .confirmationDialog(
                "Dosyalar da silinsin mi?",
                isPresented: Binding(get: { pendingDeletion != nil }, set: { if !$0 { pendingDeletion = nil } }),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { item in
                Button("Dosyaları sil", role: .destructive) {
                    for url in item.existingFiles {
                        try? FileManager.default.removeItem(at: url)
                    }
                    withAnimation(.smooth) { modelContext.delete(item) }
                }
                Button("Yalnızca kitaplıktan kaldır") {
                    withAnimation(.smooth) { modelContext.delete(item) }
                }
                Button("Vazgeç", role: .cancel) {}
            } message: { item in
                Text("\"\(item.title)\" dosyaları cihazdan kalıcı olarak silinir.")
            }
        }
    }

    @ViewBuilder
    private func menu(for item: LibraryItem) -> some View {
        let files = item.existingFiles
        if let first = files.first {
            Button {
                previewURL = first
            } label: {
                Label("Oynat", systemImage: "play.fill")
            }
            ShareLink(items: files) {
                Label("Paylaş", systemImage: "square.and.arrow.up")
            }
            Button {
                openInFiles(first.deletingLastPathComponent())
            } label: {
                Label("Dosyalar'da göster", systemImage: "folder")
            }
        }
        if let spec = item.spec {
            Button {
                downloads.enqueue(spec)
            } label: {
                Label("Yeniden indir", systemImage: "arrow.down.circle")
            }
        }
        Button {
            UIPasteboard.general.string = item.sourceURL
        } label: {
            Label("Bağlantıyı kopyala", systemImage: "link")
        }
        Divider()
        Button(role: .destructive) {
            pendingDeletion = item
        } label: {
            Label("Sil", systemImage: "trash")
        }
    }

    private func openInFiles(_ folder: URL) {
        // The Files app opens a folder of this app's Documents through this scheme.
        var components = URLComponents()
        components.scheme = "shareddocuments"
        components.path = folder.path
        if let url = components.url {
            UIApplication.shared.open(url)
        }
    }
}

enum LibraryFilter: String, CaseIterable, Identifiable {
    case all, video, audio

    var id: Self { self }

    var label: String {
        switch self {
        case .all: "Tümü"
        case .video: "Video"
        case .audio: "Ses"
        }
    }

    var symbol: String? {
        switch self {
        case .all: "square.grid.2x2"
        case .video: "film"
        case .audio: "waveform"
        }
    }
}

private struct LibraryTile: View {
    let item: LibraryItem
    let open: () -> Void

    var body: some View {
        Button(action: open) {
            VStack(alignment: .leading, spacing: 10) {
                MediaThumbnail(urlString: item.thumbnail, kind: item.kind, cornerRadius: 18)
                    .aspectRatio(item.kind == .video ? 16 / 9 : 1, contentMode: .fit)
                    .overlay(alignment: .topTrailing) {
                        Image(systemName: item.kind.symbol)
                            .font(.caption.weight(.bold))
                            .padding(7)
                            .glassEffect(.regular, in: .circle)
                            .padding(8)
                    }
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .padding(.horizontal, 4)
            }
            .padding(8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(.rect(cornerRadius: 26))
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 26))
        .opacity(item.existingFiles.isEmpty ? 0.55 : 1)
    }

    private var subtitle: String {
        var parts: [String] = []
        if let size = Format.bytes(Double(item.sizeBytes)) {
            parts.append(size)
        }
        parts.append(Format.relativeDate(item.completedAt))
        if item.existingFiles.isEmpty {
            parts.insert("Dosya yok", at: 0)
        }
        return parts.joined(separator: " · ")
    }
}
