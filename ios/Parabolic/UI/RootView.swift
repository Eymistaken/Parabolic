import SwiftUI

enum AppTab: Hashable {
    case downloads, library, settings
}

struct RootView: View {
    @Environment(DownloadManager.self) private var downloads
    @State private var tab: AppTab = .downloads

    var body: some View {
        @Bindable var downloads = downloads

        TabView(selection: $tab) {
            Tab("İndirmeler", systemImage: "arrow.down.circle", value: AppTab.downloads) {
                DownloadsView()
            }
            .badge(downloads.activeTasks.count)

            Tab("Kitaplık", systemImage: "square.stack", value: AppTab.library) {
                LibraryView()
            }

            Tab("Ayarlar", systemImage: "gearshape", value: AppTab.settings) {
                SettingsView()
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        .tabViewBottomAccessory {
            DownloadAccessory(tab: $tab)
        }
        .sheet(isPresented: $downloads.isAddSheetPresented, onDismiss: { downloads.pendingURL = nil }) {
            AddDownloadSheet(initialURL: downloads.pendingURL)
        }
        .task {
            await downloads.refreshEngineVersion()
            await downloads.updateEngine(force: false)
        }
    }
}

/// Lives above the tab bar: the running download when there is one, a quick
/// "new download" button otherwise.
struct DownloadAccessory: View {
    @Environment(DownloadManager.self) private var downloads
    @Environment(\.tabViewBottomAccessoryPlacement) private var placement
    @Binding var tab: AppTab

    var body: some View {
        if let task = downloads.spotlight {
            Button {
                tab = .downloads
            } label: {
                HStack(spacing: 12) {
                    ZStack {
                        MediaThumbnail(urlString: task.spec.thumbnail, kind: task.spec.kind, cornerRadius: 15)
                            .frame(width: 30, height: 30)
                            .clipShape(.circle)
                        ProgressRing(value: task.progress, lineWidth: 2.5)
                            .frame(width: 36, height: 36)
                    }
                    VStack(alignment: .leading, spacing: 1) {
                        Text(task.spec.title)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        if placement != .inline {
                            Text(statusLine(for: task))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    Spacer(minLength: 4)
                    if let percent = Format.percent(task.progress) {
                        Text(percent)
                            .font(.subheadline.monospacedDigit().weight(.medium))
                            .contentTransition(.numericText())
                    }
                }
                .padding(.horizontal, 12)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
        } else {
            Button {
                downloads.present(url: nil)
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "link.badge.plus")
                        .foregroundStyle(Color.accentColor)
                    Text("Bağlantıdan indir")
                        .font(.subheadline.weight(.medium))
                    Spacer(minLength: 0)
                    if placement != .inline {
                        Image(systemName: "plus.circle.fill")
                            .foregroundStyle(Color.accentColor)
                    }
                }
                .padding(.horizontal, 16)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
        }
    }

    private func statusLine(for task: DownloadTask) -> String {
        var parts = [task.stage.label]
        if task.itemCount > 0 {
            parts.append("\(task.itemIndex)/\(task.itemCount)")
        }
        if let speed = Format.speed(task.speed) {
            parts.append(speed)
        }
        let active = downloads.activeTasks.count
        if active > 1 {
            parts.append("+\(active - 1) indirme")
        }
        return parts.joined(separator: " · ")
    }
}
