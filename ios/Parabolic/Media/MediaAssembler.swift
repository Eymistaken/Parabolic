import AVFoundation
import CoreMedia
import Foundation

/// Turns the separate streams yt-dlp downloaded into one tagged file, using
/// AVFoundation instead of the ffmpeg that cannot run on iOS.
///
/// Nothing is re-encoded: tracks are copied with the passthrough preset, so a
/// 4K download is merged in seconds and keeps its original quality.
enum MediaAssembler {
    struct Metadata: Sendable {
        var title: String
        var artist: String?
        var album: String?
        var comment: String?
        var releaseDate: String?
        var artwork: Data?
    }

    enum AssemblyError: LocalizedError {
        case missingTrack(String)
        case exportUnavailable

        var errorDescription: String? {
            switch self {
            case .missingTrack(let kind): "İndirilen dosyada \(kind) izi bulunamadı."
            case .exportUnavailable: "AVFoundation bu dosyayı dışa aktaramadı."
            }
        }
    }

    /// Extensions AVFoundation can open and write back as MPEG-4.
    static let mpeg4Extensions: Set<String> = ["mp4", "m4a", "m4v", "mov", "3gp"]

    /// Merges `video` (optional) and `audio` into `destination`, removing the
    /// `cuts` time ranges (in seconds) and writing `metadata`.
    static func assemble(
        video: URL?,
        audio: URL?,
        cuts: [ClosedRange<Double>],
        metadata: Metadata?,
        destination: URL,
        fileType: AVFileType
    ) async throws {
        let composition = AVMutableComposition()
        var videoEnd: CMTime?

        if let video {
            let asset = AVURLAsset(url: video)
            guard let track = try await asset.loadTracks(withMediaType: .video).first else {
                throw AssemblyError.missingTrack("görüntü")
            }
            let range = try await track.load(.timeRange)
            let transform = try await track.load(.preferredTransform)
            let target = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)
            try target?.insertTimeRange(range, of: track, at: .zero)
            target?.preferredTransform = transform
            videoEnd = range.duration
        }

        if let audio {
            let asset = AVURLAsset(url: audio)
            guard let track = try await asset.loadTracks(withMediaType: .audio).first else {
                throw AssemblyError.missingTrack("ses")
            }
            var range = try await track.load(.timeRange)
            // Keep the audio from running past the picture.
            if let videoEnd, range.duration > videoEnd {
                range = CMTimeRange(start: range.start, duration: videoEnd)
            }
            let target = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)
            try target?.insertTimeRange(range, of: track, at: .zero)
        }

        // Remove sponsor segments back to front so earlier ranges keep their offsets.
        let duration = composition.duration.seconds
        for cut in cuts.sorted(by: { $0.lowerBound > $1.lowerBound }) {
            let start = max(0, cut.lowerBound)
            let end = min(duration, cut.upperBound)
            guard end - start > 0.25 else { continue }
            composition.removeTimeRange(CMTimeRange(
                start: CMTime(seconds: start, preferredTimescale: 600),
                end: CMTime(seconds: end, preferredTimescale: 600)
            ))
        }

        guard let session = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetPassthrough) else {
            throw AssemblyError.exportUnavailable
        }
        session.shouldOptimizeForNetworkUse = true
        if let metadata {
            session.metadata = items(for: metadata)
        }
        try? FileManager.default.removeItem(at: destination)
        try await session.export(to: destination, as: fileType)
    }

    /// iTunes-style atoms: what the Music app, Files and most players read from MPEG-4.
    private static func items(for metadata: Metadata) -> [AVMetadataItem] {
        var items: [AVMetadataItem] = []

        func add(_ identifier: AVMetadataIdentifier, _ value: (NSCopying & NSObjectProtocol)?, dataType: String? = nil) {
            guard let value else { return }
            let item = AVMutableMetadataItem()
            item.identifier = identifier
            item.value = value
            item.extendedLanguageTag = "und"
            if let dataType {
                item.dataType = dataType
            }
            items.append(item)
        }

        add(.iTunesMetadataSongName, metadata.title as NSString)
        add(.iTunesMetadataArtist, metadata.artist.map { $0 as NSString })
        add(.iTunesMetadataAlbum, metadata.album.map { $0 as NSString })
        add(.iTunesMetadataUserComment, metadata.comment.map { $0 as NSString })
        add(.iTunesMetadataReleaseDate, metadata.releaseDate.map { $0 as NSString })
        add(.iTunesMetadataEncodingTool, "Parabolic" as NSString)
        if let artwork = metadata.artwork {
            add(.iTunesMetadataCoverArt, artwork as NSData, dataType: kCMMetadataBaseDataType_JPEG as String)
        }
        return items
    }

    /// Whether AVFoundation can read this file at all (MPEG-4 family, real tracks).
    static func isReadable(_ url: URL) async -> Bool {
        guard mpeg4Extensions.contains(url.pathExtension.lowercased()) else { return false }
        let asset = AVURLAsset(url: url)
        return ((try? await asset.load(.tracks)) ?? []).isEmpty == false
    }
}
