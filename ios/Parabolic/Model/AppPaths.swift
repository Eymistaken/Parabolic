import Foundation

/// Where everything lives inside the app container.
///
/// Finished media goes to Documents, which the Files app shows under
/// "On My iPhone › Parabolic" (UIFileSharingEnabled + LSSupportsOpeningDocumentsInPlace).
enum AppPaths {
    private static let fileManager = FileManager.default

    static var documents: URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    static var videos: URL { ensure(documents.appending(path: "Videolar", directoryHint: .isDirectory)) }
    static var music: URL { ensure(documents.appending(path: "Müzik", directoryHint: .isDirectory)) }

    static func library(for kind: MediaKind) -> URL {
        kind == .video ? videos : music
    }

    static var engineData: URL {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return ensure(base.appending(path: "Engine", directoryHint: .isDirectory))
    }

    static var engineCache: URL {
        let base = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return ensure(base.appending(path: "Engine", directoryHint: .isDirectory))
    }

    /// Scratch space for one task; yt-dlp writes its parts here.
    static func workDirectory(for id: UUID) -> URL {
        ensure(fileManager.temporaryDirectory.appending(path: "work-\(id.uuidString)", directoryHint: .isDirectory))
    }

    static func removeWorkDirectory(for id: UUID) {
        try? fileManager.removeItem(at: fileManager.temporaryDirectory.appending(path: "work-\(id.uuidString)"))
    }

    /// Path relative to Documents: the container path changes between installs.
    static func relativePath(of url: URL) -> String {
        let base = documents.standardizedFileURL.path
        let path = url.standardizedFileURL.path
        if path.hasPrefix(base + "/") {
            return String(path.dropFirst(base.count + 1))
        }
        return path
    }

    static func resolve(relativePath: String) -> URL {
        if relativePath.hasPrefix("/") {
            return URL(fileURLWithPath: relativePath)
        }
        return documents.appending(path: relativePath)
    }

    @discardableResult
    static func ensure(_ url: URL) -> URL {
        try? fileManager.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    /// A file name that is safe on every file system and still readable.
    static func sanitizedName(_ name: String, fallback: String = "Parabolic") -> String {
        let forbidden = CharacterSet(charactersIn: "/\\:?%*|\"<>\n\r\t").union(.controlCharacters)
        var cleaned = name.unicodeScalars
            .map { forbidden.contains($0) ? "_" : String($0) }
            .joined()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        while cleaned.hasPrefix(".") { cleaned.removeFirst() }
        if cleaned.count > 150 {
            cleaned = String(cleaned.prefix(150)).trimmingCharacters(in: .whitespaces)
        }
        return cleaned.isEmpty ? fallback : cleaned
    }

    /// `folder/name.ext`, or `folder/name (2).ext` if that is taken.
    static func uniqueURL(in folder: URL, name: String, ext: String) -> URL {
        var candidate = folder.appending(path: "\(name).\(ext)")
        var counter = 2
        while fileManager.fileExists(atPath: candidate.path) {
            candidate = folder.appending(path: "\(name) (\(counter)).\(ext)")
            counter += 1
        }
        return candidate
    }
}
