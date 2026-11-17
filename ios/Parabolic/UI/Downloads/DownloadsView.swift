import QuickLook
import SwiftUI
import UIKit

struct DownloadsView: View {
    @Environment(DownloadManager.self) private var downloads
    @State private var previewURL: URL?
    @State private var logTask: DownloadTask?

    var body: some View {
        NavigationStack {
            ScrollView {
                if downloads.tasks.isEmpty {
                    GlassEmptyState(
                        symbol: "arrow.down.circle",
                        title: "Henüz indirme yok",
                        message: "YouTube ve yt-dlp'nin desteklediği yüzlerce siteden video ya da ses indir."
                    ) {
                        Button {
                            downloads.present(url: nil)
                        } label: {
                            Label("Bağlantı ekle", systemImage: "link.badge.plus")
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                        }
                        .buttonStyle(.glassProminent)
                        .controlSize(.large)
                    }
                    .padding(.top, 80)
                } else {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        let active = downloads.activeTasks
                        let finished = downloads.finishedTasks
                        if !active.isEmpty {
                            sectionHeader("Sürüyor", count: active.count)
                            ForEach(active) { task in
                                DownloadCard(task: task, previewURL: $previewURL, logTask: $logTask)
                                    .transition(.blurReplace)
                            }
                        }
                        if !finished.isEmpty {
                            sectionHeader("Biten", count: finished.count)
                                .padding(.top, active.isEmpty ? 0 : 10)
                            ForEach(finished) { task in
                                DownloadCard(task: task, previewURL: $previewURL, logTask: $logTask)
                                    .transition(.blurReplace)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                    .animation(.smooth, value: downloads.tasks.map(\.state))
                }
            }
            .background { AmbientBackground() }
            .navigationTitle("İndirmeler")
            .toolbar {
                if !downloads.finishedTasks.isEmpty {
                    ToolbarItem(placement: .topBarLeading) {
                        Button {
                            withAnimation(.smooth) { downloads.clearFinished() }
                        } label: {
                            Label("Bitenleri temizle", systemImage: "checklist.checked")
                        }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        downloads.present(url: nil)
                    } label: {
                        Label("Yeni indirme", systemImage: "plus")
                    }
                    .buttonStyle(.glassProminent)
                }
            }
            .quickLookPreview($previewURL)
            .sheet(item: $logTask) { task in
                DownloadLogSheet(task: task)
            }
        }
    }

    private func sectionHeader(_ title: String, count: Int) -> some View {
        HStack {
            Text(title)
                .font(.title3.bold())
            Text("\(count)")
                .font(.subheadline.monospacedDigit().weight(.semibold))
                .padding(.horizontal, 9)
                .padding(.vertical, 3)
                .glassEffect(.regular, in: .capsule)
            Spacer()
        }
        .padding(.horizontal, 4)
        .padding(.top, 8)
    }
}

struct DownloadCard: View {
    @Environment(DownloadManager.self) private var downloads
    let task: DownloadTask
    @Binding var previewURL: URL?
    @Binding var logTask: DownloadTask?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 14) {
                MediaThumbnail(urlString: task.spec.thumbnail, kind: task.spec.kind, cornerRadius: 16)
                    .frame(width: task.spec.kind == .video ? 104 : 64, height: 64)
                VStack(alignment: .leading, spacing: 5) {
                    Text(task.spec.title)
                        .font(.headline)
                        .lineLimit(2)
                    statusLabel
                }
                Spacer(minLength: 0)
            }

            if task.state.isActive {
                VStack(alignment: .leading, spacing: 6) {
                    GlassProgressBar(value: task.progress)
                    HStack {
                        Text(detailLine)
                            .lineLimit(1)
                        Spacer()
                        if let percent = Format.percent(task.progress) {
                            Text(percent)
                                .contentTransition(.numericText())
                        }
                    }
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
                }
            }

            if let error = task.error, task.state == .failed {
                Label(error, systemImage: "exclamationmark.triangle.fill")
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .lineLimit(4)
            }
            if let notice = task.notice {
                Label(notice, systemImage: "info.circle")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            actions
        }
        .glassCard(cornerRadius: 28)
        .contextMenu {
            Button {
                UIPasteboard.general.string = task.spec.url
            } label: {
                Label("Bağlantıyı kopyala", systemImage: "doc.on.doc")
            }
            Button {
                logTask = task
            } label: {
                Label("Günlüğü göster", systemImage: "text.alignleft")
            }
            if task.state.isTerminal {
                Button(role: .destructive) {
                    withAnimation(.smooth) { downloads.remove(task) }
                } label: {
                    Label("Listeden kaldır", systemImage: "xmark")
                }
            }
        }
    }

    private var statusLabel: some View {
        HStack(spacing: 6) {
            switch task.state {
            case .done:
                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                Text(task.outputs.count > 1 ? "\(task.outputs.count) dosya hazır" : "Hazır")
            case .failed:
                Image(systemName: "xmark.octagon.fill").foregroundStyle(.red)
                Text("Başarısız")
            case .canceled:
                Image(systemName: "stop.circle.fill").foregroundStyle(.secondary)
                Text("İptal edildi")
            default:
                Image(systemName: task.stage.symbol)
                    .symbolEffect(.pulse, isActive: task.state == .running)
                    .foregroundStyle(Color.accentColor)
                Text(task.stage.label)
                if task.itemCount > 0 {
                    Text("· \(task.itemIndex)/\(task.itemCount)")
                        .monospacedDigit()
                }
            }
        }
        .font(.subheadline)
        .foregroundStyle(.secondary)
    }

    private var detailLine: String {
        if task.itemCount > 0, let title = task.itemTitle {
            return title
        }
        return [Format.speed(task.speed), Format.remaining(task.eta), Format.bytes(task.totalBytes)]
            .compactMap { $0 }
            .joined(separator: " · ")
    }

    @ViewBuilder
    private var actions: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 8) {
                switch task.state {
                case .queued, .running, .finishing:
                    Button(role: .destructive) {
                        downloads.cancel(task)
                    } label: {
                        Label("İptal", systemImage: "stop.fill")
                    }
                    .buttonStyle(.glass)

                case .done:
                    if let first = task.outputs.first {
                        Button {
                            previewURL = first
                        } label: {
                            Label("Aç", systemImage: "play.fill")
                        }
                        .buttonStyle(.glassProminent)
                        ShareLink(items: task.outputs) {
                            Label("Paylaş", systemImage: "square.and.arrow.up")
                        }
                        .buttonStyle(.glass)
                    }

                case .failed, .canceled:
                    Button {
                        withAnimation(.smooth) { downloads.retry(task) }
                    } label: {
                        Label("Yeniden dene", systemImage: "arrow.clockwise")
                    }
                    .buttonStyle(.glassProminent)
                }

                Spacer(minLength: 0)

                Button {
                    logTask = task
                } label: {
                    Image(systemName: "text.alignleft")
                }
                .buttonStyle(.glass)
                .accessibilityLabel("Günlük")

                if task.state.isTerminal {
                    Button {
                        withAnimation(.smooth) { downloads.remove(task) }
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .buttonStyle(.glass)
                    .accessibilityLabel("Listeden kaldır")
                }
            }
            .controlSize(.small)
            .labelStyle(.titleAndIcon)
        }
    }
}

struct DownloadLogSheet: View {
    let task: DownloadTask
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    if task.log.isEmpty {
                        Text("Henüz çıktı yok.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(Array(task.log.enumerated()), id: \.offset) { _, line in
                        Text(line)
                            .font(.caption.monospaced())
                            .foregroundStyle(line.hasPrefix("ERROR") ? Color.red : line.hasPrefix("WARNING") ? Color.orange : Color.primary)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .glassCard(cornerRadius: 24)
                .padding()
            }
            .background { AmbientBackground() }
            .navigationTitle("Günlük")
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
                ToolbarItem(placement: .primaryAction) {
                    ShareLink(item: ([task.spec.url] + task.log).joined(separator: "\n")) {
                        Image(systemName: "square.and.arrow.up")
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
