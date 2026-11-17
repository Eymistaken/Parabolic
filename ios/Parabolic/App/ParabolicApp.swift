import SwiftData
import SwiftUI

@main
struct ParabolicApp: App {
    @State private var downloads = DownloadManager.shared
    @State private var settings = AppSettings.shared
    private let container: ModelContainer

    init() {
        let container: ModelContainer
        do {
            container = try ModelContainer(for: LibraryItem.self)
        } catch {
            // A broken store must not keep the app from launching; the files
            // themselves are still in Documents.
            container = try! ModelContainer(
                for: LibraryItem.self,
                configurations: ModelConfiguration(isStoredInMemoryOnly: true)
            )
        }
        self.container = container
        DownloadManager.shared.attach(container)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(downloads)
                .environment(settings)
                .preferredColorScheme(settings.theme.colorScheme)
                .onOpenURL { url in
                    downloads.handleIncoming(url)
                }
        }
        .modelContainer(container)
    }
}
