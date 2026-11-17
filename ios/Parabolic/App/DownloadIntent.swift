import AppIntents
import Foundation

/// "Parabolic ile indir" in Shortcuts: opens the app with the link ready to
/// download. Added to the share sheet through a shortcut, this is the iOS
/// counterpart of Android's "share to Parabolic".
struct DownloadWithParabolicIntent: AppIntent {
    static var title: LocalizedStringResource = "Parabolic ile indir"
    static var description = IntentDescription("Bir bağlantıyı Parabolic'te açar ve indirme seçeneklerini gösterir.")
    static var openAppWhenRun: Bool = true

    @Parameter(title: "Bağlantı")
    var url: URL

    @MainActor
    func perform() async throws -> some IntentResult {
        DownloadManager.shared.present(url: url.absoluteString)
        return .result()
    }
}
