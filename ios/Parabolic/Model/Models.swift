import Foundation
import SwiftUI

enum MediaKind: String, Codable, CaseIterable, Identifiable, Sendable {
    case video, audio

    var id: Self { self }

    var label: String {
        switch self {
        case .video: "Video"
        case .audio: "Ses"
        }
    }

    var symbol: String {
        switch self {
        case .video: "film"
        case .audio: "waveform"
        }
    }
}

enum VideoQuality: String, Codable, CaseIterable, Identifiable, Sendable {
    case best, uhd, qhd, fhd, hd, sd, low

    var id: Self { self }

    var label: String {
        switch self {
        case .best: "En iyi"
        case .uhd: "2160p"
        case .qhd: "1440p"
        case .fhd: "1080p"
        case .hd: "720p"
        case .sd: "480p"
        case .low: "360p"
        }
    }

    var maxHeight: Int? {
        switch self {
        case .best: nil
        case .uhd: 2160
        case .qhd: 1440
        case .fhd: 1080
        case .hd: 720
        case .sd: 480
        case .low: 360
        }
    }

    /// Qualities worth offering for a source whose tallest stream is `height`.
    static func offered(forHeights heights: [Int]) -> [VideoQuality] {
        guard let tallest = heights.max() else { return allCases }
        return allCases.filter { quality in
            guard let limit = quality.maxHeight else { return true }
            return limit <= max(tallest, 360)
        }
    }
}

enum VideoCodec: String, Codable, CaseIterable, Identifiable, Sendable {
    case compatible, efficient

    var id: Self { self }

    var label: String {
        switch self {
        case .compatible: "H.264"
        case .efficient: "AV1 / HEVC"
        }
    }

    var detail: String {
        switch self {
        case .compatible: "Her cihazda oynar, YouTube'da en fazla 1080p."
        case .efficient: "4K'ya kadar; AV1 için A17 Pro, M3 veya sonrası gerekir."
        }
    }
}

enum AudioFormat: String, Codable, CaseIterable, Identifiable, Sendable {
    case m4a, original

    var id: Self { self }

    var label: String {
        switch self {
        case .m4a: "M4A (AAC)"
        case .original: "Kaynak"
        }
    }

    var detail: String {
        switch self {
        case .m4a: "Kapak ve etiketlerle, Müzik ve Dosyalar'da oynar."
        case .original: "Sitenin sunduğu en iyi ses, dönüştürülmeden."
        }
    }
}

enum UpdateChannel: String, Codable, CaseIterable, Identifiable, Sendable {
    case stable, nightly, master

    var id: Self { self }

    var label: String {
        switch self {
        case .stable: "Kararlı"
        case .nightly: "Nightly"
        case .master: "Master"
        }
    }
}

enum ThemeOption: String, CaseIterable, Identifiable, Sendable {
    case system, light, dark

    var id: Self { self }

    var label: String {
        switch self {
        case .system: "Sistem"
        case .light: "Açık"
        case .dark: "Koyu"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

// MARK: - Probe

struct PlaylistEntry: Decodable, Hashable, Sendable {
    let url: String
    let title: String?
    let duration: Double?
    let thumbnail: String?
}

/// What yt-dlp learned about a link before anything is downloaded.
struct MediaProbe: Decodable, Sendable {
    let id: String?
    let title: String
    let uploader: String?
    let thumbnail: String?
    let duration: Double?
    let isPlaylist: Bool
    let playlistCount: Int?
    let entries: [PlaylistEntry]
    let heights: [Int]
    let hasVideo: Bool
    let extractor: String?
    let webpageUrl: String?
}

// MARK: - Download spec

struct DownloadSpec: Codable, Hashable, Sendable {
    var url: String
    var title: String
    var thumbnail: String?
    var uploader: String?
    var kind: MediaKind
    var quality: VideoQuality
    var codec: VideoCodec
    var audioFormat: AudioFormat
    var playlist: Bool
    var embedMetadata: Bool
    var embedArtwork: Bool
    var cropArtwork: Bool
    var subtitles: Bool
    var subtitleLanguages: String
    var sponsorBlock: Bool
    var saveToPhotos: Bool
}

// MARK: - Live task state

enum DownloadState: String, Sendable {
    case queued, running, finishing, done, failed, canceled

    var isTerminal: Bool { self == .done || self == .failed || self == .canceled }
    var isActive: Bool { !isTerminal }
}

enum DownloadStage: String, Sendable {
    case waiting, preparing, video, audio, downloading, merging, tagging, saving, done

    var label: String {
        switch self {
        case .waiting: "Sırada"
        case .preparing: "Hazırlanıyor"
        case .video: "Video indiriliyor"
        case .audio: "Ses indiriliyor"
        case .downloading: "İndiriliyor"
        case .merging: "Birleştiriliyor"
        case .tagging: "Etiketler yazılıyor"
        case .saving: "Kaydediliyor"
        case .done: "Tamamlandı"
        }
    }

    var symbol: String {
        switch self {
        case .waiting: "clock"
        case .preparing: "sparkle.magnifyingglass"
        case .video: "film"
        case .audio: "waveform"
        case .downloading: "arrow.down"
        case .merging: "square.stack.3d.down.right"
        case .tagging: "tag"
        case .saving: "tray.and.arrow.down"
        case .done: "checkmark"
        }
    }
}

@MainActor
@Observable
final class DownloadTask: Identifiable {
    let id = UUID()
    let spec: DownloadSpec
    let createdAt = Date()

    var state: DownloadState = .queued
    var stage: DownloadStage = .waiting
    /// 1-based playlist position and length; 0 for single videos.
    var itemIndex = 0
    var itemCount = 0
    var itemTitle: String?
    /// Overall 0...1, or nil while indeterminate.
    var progress: Double?
    var speed: Double?
    var eta: Double?
    var totalBytes: Double?
    var error: String?
    var notice: String?
    var log: [String] = []
    var outputs: [URL] = []

    init(spec: DownloadSpec) {
        self.spec = spec
    }

    func appendLog(_ line: String) {
        log.append(line)
        if log.count > 400 {
            log.removeFirst(log.count - 400)
        }
    }
}
