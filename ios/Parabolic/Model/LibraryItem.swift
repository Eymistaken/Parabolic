import Foundation
import SwiftData

/// A finished download, kept in the library (SwiftData).
@Model
final class LibraryItem {
    @Attribute(.unique) var id: UUID
    var title: String
    var sourceURL: String
    var thumbnail: String?
    var uploader: String?
    var kindRaw: String
    /// Relative to Documents, because the container path changes between installs.
    var relativePaths: [String]
    var sizeBytes: Int64
    var completedAt: Date
    var specData: Data?

    init(
        title: String,
        sourceURL: String,
        thumbnail: String?,
        uploader: String?,
        kind: MediaKind,
        relativePaths: [String],
        sizeBytes: Int64,
        spec: DownloadSpec?
    ) {
        self.id = UUID()
        self.title = title
        self.sourceURL = sourceURL
        self.thumbnail = thumbnail
        self.uploader = uploader
        self.kindRaw = kind.rawValue
        self.relativePaths = relativePaths
        self.sizeBytes = sizeBytes
        self.completedAt = Date()
        self.specData = spec.flatMap { try? JSONEncoder().encode($0) }
    }

    var kind: MediaKind { MediaKind(rawValue: kindRaw) ?? .video }

    var spec: DownloadSpec? {
        specData.flatMap { try? JSONDecoder().decode(DownloadSpec.self, from: $0) }
    }

    var files: [URL] { relativePaths.map(AppPaths.resolve(relativePath:)) }

    var existingFiles: [URL] {
        files.filter { FileManager.default.fileExists(atPath: $0.path) }
    }
}
