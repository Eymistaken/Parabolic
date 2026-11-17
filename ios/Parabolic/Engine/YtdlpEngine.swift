import Foundation

enum EngineError: LocalizedError {
    case engineUnavailable(String)
    case bridge(String)
    case ytdlp(String)

    var errorDescription: String? {
        switch self {
        case .engineUnavailable(let message): "İndirme motoru başlatılamadı: \(message)"
        case .bridge(let message): message
        case .ytdlp(let message): message
        }
    }
}

// MARK: - Events from Python

struct EngineEvent: Decodable, Sendable {
    let type: String
    let id: String
    let status: String?
    let part: Int?
    let parts: Int?
    let role: String?
    let downloaded: Double?
    let total: Double?
    let speed: Double?
    let eta: Double?
    let line: String?
}

/// Routes events emitted by Python to whoever registered for that task id.
final class EngineEvents: @unchecked Sendable {
    static let shared = EngineEvents()

    private let lock = NSLock()
    private var handlers: [String: @Sendable (EngineEvent) -> Void] = [:]
    private let decoder = JSONDecoder()

    func subscribe(_ id: String, handler: @escaping @Sendable (EngineEvent) -> Void) {
        lock.lock()
        handlers[id] = handler
        lock.unlock()
    }

    func unsubscribe(_ id: String) {
        lock.lock()
        handlers[id] = nil
        lock.unlock()
    }

    func deliver(_ data: Data) {
        guard let event = try? decoder.decode(EngineEvent.self, from: data) else { return }
        lock.lock()
        let handler = handlers[event.id]
        lock.unlock()
        handler?(event)
    }
}

// MARK: - Replies

private struct Envelope<Result: Decodable>: Decodable {
    let ok: Bool
    let result: Result?
    let error: String?
    let cancelled: Bool?
}

private struct Empty: Decodable {}

struct EngineVersion: Decodable, Sendable {
    let loaded: String?
    let source: String?
    let bundled: String?
    let updated: String?
    let python: String?

    /// The version that is (or will be, on next launch) in use.
    var effective: String? { loaded ?? updated ?? bundled }
}

struct UpdateOutcome: Decodable, Sendable {
    let status: String
    let version: String
    let restart: Bool?
}

struct ResetOutcome: Decodable, Sendable {
    let restart: Bool?
}

struct DownloadOutcome: Decodable, Sendable {
    struct Part: Decodable, Sendable {
        let path: String
        let role: String
        let ext: String
        let vcodec: String?
        let acodec: String?
        let height: Int?

        var url: URL { URL(fileURLWithPath: path) }
    }

    let parts: [Part]
    let subtitles: [String]
    let id: String?
    let title: String
    let uploader: String?
    let album: String?
    let extractor: String?
    let thumbnail: String?
    let duration: Double?
    let webpageUrl: String?
    let uploadDate: String?
    let description: String?
    let warnings: [String]
}

// MARK: - Engine

/// Typed Swift façade over `parabolic_bridge.py`.
enum YtdlpEngine {
    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return decoder
    }()

    private static func request<Result: Decodable>(_ payload: [String: Any], as type: Result.Type) async throws -> Result {
        let data = try await PythonRuntime.shared.call(payload)
        let envelope = try decoder.decode(Envelope<Result>.self, from: data)
        if envelope.cancelled == true {
            throw CancellationError()
        }
        guard envelope.ok, let result = envelope.result else {
            throw EngineError.ytdlp(envelope.error ?? "Bilinmeyen hata")
        }
        return result
    }

    static func version() async throws -> EngineVersion {
        try await request(["op": "version"], as: EngineVersion.self)
    }

    static func probe(url: String, wholePlaylist: Bool) async throws -> MediaProbe {
        try await request(["op": "probe", "url": url, "playlist": wholePlaylist], as: MediaProbe.self)
    }

    static func download(
        taskID: String,
        url: String,
        spec: DownloadSpec,
        outputDirectory: URL,
        fragments: Int
    ) async throws -> DownloadOutcome {
        var payload: [String: Any] = [
            "op": "download",
            "id": taskID,
            "url": url,
            "format": formatSelector(for: spec),
            "outdir": outputDirectory.path,
            "fragments": fragments,
        ]
        if spec.kind == .video && spec.subtitles {
            payload["subtitles"] = true
            payload["subtitle_langs"] = spec.subtitleLanguages
        }
        return try await request(payload, as: DownloadOutcome.self)
    }

    static func cancel(taskID: String) async {
        _ = try? await request(["op": "cancel", "id": taskID], as: Empty.self)
    }

    static func forget(taskID: String) async {
        _ = try? await request(["op": "forget", "id": taskID], as: Empty.self)
    }

    static func update(channel: UpdateChannel) async throws -> UpdateOutcome {
        try await request(["op": "update", "channel": channel.rawValue], as: UpdateOutcome.self)
    }

    static func resetToBundled() async throws -> ResetOutcome {
        try await request(["op": "reset"], as: ResetOutcome.self)
    }

    /// yt-dlp format selector limited to what AVFoundation can mux and play.
    ///
    /// There is no ffmpeg on iOS, so video and audio must arrive in MPEG-4
    /// friendly codecs (H.264/HEVC/AV1 video, AAC audio) for the app to merge
    /// them into an .mp4 without re-encoding.
    static func formatSelector(for spec: DownloadSpec) -> String {
        switch spec.kind {
        case .audio:
            switch spec.audioFormat {
            case .m4a: return "ba[ext=m4a]/ba[acodec^=mp4a]/b[ext=mp4]/ba/b"
            case .original: return "ba/b"
            }
        case .video:
            let height = spec.quality.maxHeight.map { "[height<=\($0)]" } ?? ""
            let compatible = [
                "bv*\(height)[vcodec^=avc1]+ba[ext=m4a]",
                "b\(height)[ext=mp4]",
            ]
            let efficient = [
                "bv*\(height)[vcodec^=av01][ext=mp4]+ba[ext=m4a]",
                "bv*\(height)[vcodec^=hvc1]+ba[ext=m4a]",
                "bv*\(height)[vcodec^=hev1]+ba[ext=m4a]",
            ]
            let fallbacks = [
                "bv*[vcodec^=avc1]+ba[ext=m4a]",
                "b[ext=mp4]",
                "b",
            ]
            let chain = (spec.codec == .efficient ? efficient + compatible : compatible) + fallbacks
            return chain.joined(separator: "/")
        }
    }
}
