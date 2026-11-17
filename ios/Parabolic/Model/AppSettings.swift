import Foundation
import Observation

/// User preferences, observable from SwiftUI and persisted to UserDefaults on every change.
@MainActor
@Observable
final class AppSettings {
    static let shared = AppSettings()

    private enum Key {
        static let theme = "theme"
        static let defaultKind = "defaultKind"
        static let defaultQuality = "defaultQuality"
        static let defaultCodec = "defaultCodec"
        static let defaultAudioFormat = "defaultAudioFormat"
        static let embedMetadata = "embedMetadata"
        static let embedArtwork = "embedArtwork"
        static let cropArtwork = "cropArtwork"
        static let subtitles = "subtitles"
        static let subtitleLanguages = "subtitleLanguages"
        static let sponsorBlock = "sponsorBlock"
        static let saveToPhotos = "saveToPhotos"
        static let concurrentDownloads = "concurrentDownloads"
        static let concurrentFragments = "concurrentFragments"
        static let notifyOnFinish = "notifyOnFinish"
        static let updateChannel = "updateChannel"
        static let autoUpdate = "autoUpdate"
        static let lastUpdateCheck = "lastUpdateCheck"
    }

    @ObservationIgnored private let defaults = UserDefaults.standard

    var theme: ThemeOption { didSet { defaults.set(theme.rawValue, forKey: Key.theme) } }

    var defaultKind: MediaKind { didSet { defaults.set(defaultKind.rawValue, forKey: Key.defaultKind) } }
    var defaultQuality: VideoQuality { didSet { defaults.set(defaultQuality.rawValue, forKey: Key.defaultQuality) } }
    var defaultCodec: VideoCodec { didSet { defaults.set(defaultCodec.rawValue, forKey: Key.defaultCodec) } }
    var defaultAudioFormat: AudioFormat { didSet { defaults.set(defaultAudioFormat.rawValue, forKey: Key.defaultAudioFormat) } }

    var embedMetadata: Bool { didSet { defaults.set(embedMetadata, forKey: Key.embedMetadata) } }
    var embedArtwork: Bool { didSet { defaults.set(embedArtwork, forKey: Key.embedArtwork) } }
    var cropArtwork: Bool { didSet { defaults.set(cropArtwork, forKey: Key.cropArtwork) } }
    var subtitles: Bool { didSet { defaults.set(subtitles, forKey: Key.subtitles) } }
    var subtitleLanguages: String { didSet { defaults.set(subtitleLanguages, forKey: Key.subtitleLanguages) } }
    var sponsorBlock: Bool { didSet { defaults.set(sponsorBlock, forKey: Key.sponsorBlock) } }
    var saveToPhotos: Bool { didSet { defaults.set(saveToPhotos, forKey: Key.saveToPhotos) } }

    var concurrentDownloads: Int { didSet { defaults.set(concurrentDownloads, forKey: Key.concurrentDownloads) } }
    var concurrentFragments: Int { didSet { defaults.set(concurrentFragments, forKey: Key.concurrentFragments) } }
    var notifyOnFinish: Bool { didSet { defaults.set(notifyOnFinish, forKey: Key.notifyOnFinish) } }

    var updateChannel: UpdateChannel { didSet { defaults.set(updateChannel.rawValue, forKey: Key.updateChannel) } }
    var autoUpdate: Bool { didSet { defaults.set(autoUpdate, forKey: Key.autoUpdate) } }

    var lastUpdateCheck: Date? {
        get { defaults.object(forKey: Key.lastUpdateCheck) as? Date }
        set { defaults.set(newValue, forKey: Key.lastUpdateCheck) }
    }

    private init() {
        let d = UserDefaults.standard
        func value<T: RawRepresentable>(_ key: String, _ fallback: T) -> T where T.RawValue == String {
            d.string(forKey: key).flatMap(T.init(rawValue:)) ?? fallback
        }
        func bool(_ key: String, _ fallback: Bool) -> Bool {
            d.object(forKey: key) == nil ? fallback : d.bool(forKey: key)
        }
        func int(_ key: String, _ fallback: Int) -> Int {
            d.object(forKey: key) == nil ? fallback : d.integer(forKey: key)
        }

        theme = value(Key.theme, ThemeOption.system)
        defaultKind = value(Key.defaultKind, MediaKind.video)
        defaultQuality = value(Key.defaultQuality, VideoQuality.fhd)
        defaultCodec = value(Key.defaultCodec, VideoCodec.compatible)
        defaultAudioFormat = value(Key.defaultAudioFormat, AudioFormat.m4a)
        embedMetadata = bool(Key.embedMetadata, true)
        embedArtwork = bool(Key.embedArtwork, true)
        cropArtwork = bool(Key.cropArtwork, true)
        subtitles = bool(Key.subtitles, false)
        subtitleLanguages = d.string(forKey: Key.subtitleLanguages) ?? "tr,en"
        sponsorBlock = bool(Key.sponsorBlock, false)
        saveToPhotos = bool(Key.saveToPhotos, false)
        concurrentDownloads = int(Key.concurrentDownloads, 2)
        concurrentFragments = int(Key.concurrentFragments, 8)
        notifyOnFinish = bool(Key.notifyOnFinish, true)
        updateChannel = value(Key.updateChannel, UpdateChannel.nightly)
        autoUpdate = bool(Key.autoUpdate, true)
    }

    func spec(url: String, probe: MediaProbe?) -> DownloadSpec {
        DownloadSpec(
            url: url,
            title: probe?.title ?? url,
            thumbnail: probe?.thumbnail,
            uploader: probe?.uploader,
            kind: probe?.hasVideo == false ? .audio : defaultKind,
            quality: defaultQuality,
            codec: defaultCodec,
            audioFormat: defaultAudioFormat,
            playlist: probe?.isPlaylist ?? false,
            embedMetadata: embedMetadata,
            embedArtwork: embedArtwork,
            cropArtwork: cropArtwork,
            subtitles: subtitles,
            subtitleLanguages: subtitleLanguages,
            sponsorBlock: sponsorBlock,
            saveToPhotos: saveToPhotos
        )
    }
}
