import AVFoundation
import Foundation
import Observation
import Photos
import SwiftData
import UIKit

/// The download queue: runs yt-dlp, assembles the result and files it away.
@MainActor
@Observable
final class DownloadManager {
    static let shared = DownloadManager()

    private(set) var tasks: [DownloadTask] = []
    private(set) var engineVersion: EngineVersion?
    private(set) var updateMessage: String?
    private(set) var isUpdating = false

    /// Set by deep links and the App Intent; the add sheet picks it up.
    var pendingURL: String?
    var isAddSheetPresented = false

    @ObservationIgnored private let settings = AppSettings.shared
    @ObservationIgnored private var modelContext: ModelContext?
    @ObservationIgnored private var runners: [UUID: Task<Void, Never>] = [:]
    @ObservationIgnored private let background = BackgroundActivity()

    private init() {}

    func attach(_ container: ModelContainer) {
        modelContext = container.mainContext
    }

    // MARK: - Derived state

    var activeTasks: [DownloadTask] { tasks.filter { $0.state.isActive } }
    var finishedTasks: [DownloadTask] { tasks.filter { $0.state.isTerminal } }

    /// The task worth showing in the tab bar accessory.
    var spotlight: DownloadTask? {
        tasks.first { $0.state == .running || $0.state == .finishing } ?? tasks.first { $0.state == .queued }
    }

    var overallProgress: Double? {
        let running = activeTasks
        guard !running.isEmpty else { return nil }
        let values = running.map { $0.progress ?? 0 }
        return values.reduce(0, +) / Double(values.count)
    }

    // MARK: - Queue

    func enqueue(_ spec: DownloadSpec) {
        let task = DownloadTask(spec: spec)
        tasks.insert(task, at: 0)
        Notifier.requestAuthorization()
        pump()
    }

    func cancel(_ task: DownloadTask) {
        guard task.state.isActive else { return }
        if task.state == .queued {
            task.state = .canceled
            task.stage = .waiting
            pump()
            return
        }
        runners[task.id]?.cancel()
        Task { await YtdlpEngine.cancel(taskID: task.id.uuidString) }
    }

    func retry(_ task: DownloadTask) {
        remove(task)
        enqueue(task.spec)
    }

    func remove(_ task: DownloadTask) {
        if task.state.isActive { cancel(task) }
        tasks.removeAll { $0.id == task.id }
    }

    func clearFinished() {
        tasks.removeAll { $0.state.isTerminal }
    }

    func present(url: String?) {
        pendingURL = url
        isAddSheetPresented = true
    }

    func handleIncoming(_ url: URL) {
        // parabolic://add?url=https://…  or  parabolic://https://…
        if let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
           let target = components.queryItems?.first(where: { $0.name == "url" })?.value {
            present(url: target)
        } else {
            let raw = url.absoluteString.replacingOccurrences(of: "parabolic://", with: "")
            present(url: raw.hasPrefix("http") ? raw : nil)
        }
    }

    private func pump() {
        let limit = max(1, settings.concurrentDownloads)
        var running = tasks.filter { $0.state == .running || $0.state == .finishing }.count
        for task in tasks.reversed() where task.state == .queued && running < limit {
            start(task)
            running += 1
        }
        syncBackgroundActivity()
    }

    private func start(_ task: DownloadTask) {
        task.state = .running
        task.stage = .preparing
        task.progress = nil
        runners[task.id] = Task { [weak self] in
            await self?.run(task)
            self?.runners[task.id] = nil
            self?.pump()
        }
    }

    private func syncBackgroundActivity() {
        let active = activeTasks
        if active.isEmpty {
            if background.isActive {
                background.end(success: !tasks.contains { $0.state == .failed })
            }
            return
        }
        let title = active.count == 1 ? active[0].spec.title : "\(active.count) indirme sürüyor"
        let subtitle = spotlight?.stage.label ?? "İndiriliyor"
        if !background.isActive {
            background.begin(title: title, subtitle: subtitle)
        }
        background.update(fraction: overallProgress ?? 0, title: title, subtitle: subtitle)
    }

    // MARK: - Running one task

    private func run(_ task: DownloadTask) async {
        let taskID = task.id.uuidString
        EngineEvents.shared.subscribe(taskID) { [weak self] event in
            Task { @MainActor in self?.apply(event, to: task) }
        }
        defer {
            EngineEvents.shared.unsubscribe(taskID)
            AppPaths.removeWorkDirectory(for: task.id)
            Task { await YtdlpEngine.forget(taskID: taskID) }
        }

        do {
            var entries: [(url: String, title: String?)] = [(task.spec.url, task.spec.title)]
            var folderName: String?
            if task.spec.playlist {
                let probe = try await YtdlpEngine.probe(url: task.spec.url, wholePlaylist: true)
                entries = probe.entries.map { (url: $0.url, title: $0.title) }
                folderName = AppPaths.sanitizedName(probe.title)
                guard !entries.isEmpty else { throw EngineError.ytdlp("Oynatma listesi boş") }
                task.itemCount = entries.count
            }

            var failures: [String] = []
            for (index, entry) in entries.enumerated() {
                try Task.checkCancellation()
                if task.spec.playlist {
                    task.itemIndex = index + 1
                    task.itemTitle = entry.title
                }
                do {
                    let output = try await downloadItem(
                        task,
                        url: entry.url,
                        index: task.spec.playlist ? index + 1 : nil,
                        folderName: folderName
                    )
                    task.outputs.append(output)
                } catch is CancellationError {
                    throw CancellationError()
                } catch {
                    guard task.spec.playlist else { throw error }
                    failures.append("\(index + 1). \(entry.title ?? entry.url): \(error.localizedDescription)")
                    task.appendLog("ERROR: \(failures.last!)")
                }
            }

            if task.outputs.isEmpty {
                throw EngineError.ytdlp(failures.first ?? "Hiçbir dosya indirilemedi")
            }
            if !failures.isEmpty {
                task.notice = "\(failures.count) öğe indirilemedi; ayrıntılar günlükte."
            }
            task.state = .done
            task.stage = .done
            task.progress = 1
            task.speed = nil
            task.eta = nil
            record(task)
            if settings.notifyOnFinish {
                Notifier.post(title: "İndirme tamamlandı", body: task.spec.title)
            }
        } catch is CancellationError {
            task.state = .canceled
            task.speed = nil
            task.eta = nil
        } catch {
            if Task.isCancelled {
                task.state = .canceled
            } else {
                task.state = .failed
                task.error = error.localizedDescription
                if settings.notifyOnFinish {
                    Notifier.post(title: "İndirme başarısız", body: task.spec.title)
                }
            }
            task.speed = nil
            task.eta = nil
        }
    }

    /// Downloads, assembles and files one video; returns the final file.
    private func downloadItem(_ task: DownloadTask, url: String, index: Int?, folderName: String?) async throws -> URL {
        let spec = task.spec
        let work = AppPaths.workDirectory(for: task.id).appending(path: "\(index ?? 0)", directoryHint: .isDirectory)
        AppPaths.ensure(work)

        task.stage = .preparing
        task.state = .running
        var attempt = 0
        var downloaded: DownloadOutcome?
        while downloaded == nil {
            attempt += 1
            do {
                downloaded = try await YtdlpEngine.download(
                    taskID: task.id.uuidString,
                    url: url,
                    spec: spec,
                    outputDirectory: work,
                    fragments: settings.concurrentFragments
                )
            } catch EngineError.ytdlp(let message) where attempt < 3 && message.contains("HTTP Error 403") {
                // YouTube now and then refuses a freshly extracted stream URL;
                // a new extraction usually gets one that works.
                task.appendLog("WARNING: HTTP 403, yeniden deneniyor (\(attempt + 1)/3)")
                try await Task.sleep(for: .seconds(2))
            }
        }
        guard let outcome = downloaded else { throw EngineError.ytdlp("İndirme başarısız") }
        try Task.checkCancellation()
        for warning in outcome.warnings where !warning.contains("impersonat") {
            task.appendLog("WARNING: \(warning)")
        }

        task.state = .finishing
        task.speed = nil
        task.eta = nil

        // Where the result goes.
        var folder = AppPaths.library(for: spec.kind)
        if let folderName {
            folder = AppPaths.ensure(folder.appending(path: folderName, directoryHint: .isDirectory))
        }
        var baseName = AppPaths.sanitizedName(outcome.title)
        if let index {
            baseName = String(format: "%03ld - ", index) + baseName
        }

        let videoPart = outcome.parts.first { $0.role == "video" } ?? outcome.parts.first { $0.role == "muxed" }
        let audioPart = outcome.parts.first { $0.role == "audio" } ?? outcome.parts.first { $0.role == "muxed" }

        // Extras, fetched only when they will be used.
        var artwork: Data?
        if spec.embedArtwork {
            task.stage = .tagging
            artwork = await Artwork.load(from: outcome.thumbnail, square: spec.kind == .audio && spec.cropArtwork)
        }
        var cuts: [ClosedRange<Double>] = []
        if spec.sponsorBlock, (outcome.extractor ?? "").lowercased().hasPrefix("youtube"), let id = outcome.id {
            cuts = await SponsorBlock.segments(for: id)
            if !cuts.isEmpty {
                task.appendLog("SponsorBlock: \(cuts.count) bölüm çıkarılıyor")
            }
        }
        let metadata: MediaAssembler.Metadata? = (spec.embedMetadata || artwork != nil) ? MediaAssembler.Metadata(
            title: spec.embedMetadata ? outcome.title : baseName,
            artist: spec.embedMetadata ? outcome.uploader : nil,
            album: spec.embedMetadata ? outcome.album : nil,
            comment: spec.embedMetadata ? outcome.webpageUrl : nil,
            releaseDate: spec.embedMetadata ? outcome.uploadDate.map(Self.isoDate) : nil,
            artwork: artwork
        ) : nil

        task.stage = videoPart != nil && audioPart != nil && videoPart?.path != audioPart?.path ? .merging : .tagging
        var finalURL: URL?

        switch spec.kind {
        case .video:
            guard let videoPart else { throw EngineError.ytdlp("Görüntü akışı indirilemedi") }
            let separateAudio = audioPart.flatMap { $0.path == videoPart.path ? nil : $0 }
            let assemblable = MediaAssembler.mpeg4Extensions.contains(videoPart.ext)
                && (separateAudio.map { MediaAssembler.mpeg4Extensions.contains($0.ext) } ?? true)
            if assemblable {
                let destination = AppPaths.uniqueURL(in: folder, name: baseName, ext: "mp4")
                do {
                    try await MediaAssembler.assemble(
                        video: videoPart.url,
                        audio: separateAudio?.url ?? videoPart.url,
                        cuts: cuts,
                        metadata: metadata,
                        destination: destination,
                        fileType: .mp4
                    )
                    finalURL = destination
                } catch {
                    if separateAudio != nil { throw error }
                    // A single complete file that AVFoundation cannot rewrite: keep it as is.
                    task.notice = "Dosya etiketlenemedi, olduğu gibi kaydedildi."
                    finalURL = try moveRaw(videoPart, to: folder, name: baseName)
                }
            } else if separateAudio == nil {
                task.notice = "Bu biçim (\(videoPart.ext)) yalnızca olduğu gibi kaydedilebildi."
                finalURL = try moveRaw(videoPart, to: folder, name: baseName)
            } else {
                throw EngineError.ytdlp("Bu site yalnızca iOS'un birleştiremediği biçimler sunuyor (\(videoPart.ext) + \(separateAudio!.ext)).")
            }

        case .audio:
            guard let audioPart else { throw EngineError.ytdlp("Ses akışı indirilemedi") }
            let readable = await MediaAssembler.isReadable(audioPart.url)
            if readable && (spec.audioFormat == .m4a || metadata != nil || !cuts.isEmpty) {
                let destination = AppPaths.uniqueURL(in: folder, name: baseName, ext: "m4a")
                do {
                    try await MediaAssembler.assemble(
                        video: nil,
                        audio: audioPart.url,
                        cuts: cuts,
                        metadata: metadata,
                        destination: destination,
                        fileType: .m4a
                    )
                    finalURL = destination
                } catch {
                    task.notice = "Ses dosyası etiketlenemedi, olduğu gibi kaydedildi."
                    finalURL = try moveRaw(audioPart, to: folder, name: baseName)
                }
            } else {
                if spec.audioFormat == .m4a {
                    task.notice = "Bu sitede M4A yok; \(audioPart.ext.uppercased()) olarak kaydedildi."
                }
                finalURL = try moveRaw(audioPart, to: folder, name: baseName)
            }
        }
        guard let savedFile = finalURL else { throw EngineError.ytdlp("Dosya kaydedilemedi") }

        // Subtitles are kept next to the video as WebVTT files.
        for subtitle in outcome.subtitles {
            let source = URL(fileURLWithPath: subtitle)
            let parts = source.lastPathComponent.split(separator: ".")
            let language = parts.count >= 3 ? String(parts[parts.count - 2]) : "sub"
            let target = AppPaths.uniqueURL(in: folder, name: "\(baseName).\(language)", ext: source.pathExtension)
            try? FileManager.default.moveItem(at: source, to: target)
        }

        if spec.saveToPhotos && spec.kind == .video {
            task.stage = .saving
            if let problem = await Self.saveToPhotos(savedFile) {
                task.notice = problem
            }
        }
        return savedFile
    }

    private func moveRaw(_ part: DownloadOutcome.Part, to folder: URL, name: String) throws -> URL {
        let ext = part.ext.isEmpty ? "bin" : part.ext
        let destination = AppPaths.uniqueURL(in: folder, name: name, ext: ext)
        try FileManager.default.moveItem(at: part.url, to: destination)
        return destination
    }

    private static func isoDate(_ ytdlpDate: String) -> String {
        // yt-dlp uses YYYYMMDD.
        guard ytdlpDate.count == 8 else { return ytdlpDate }
        let chars = Array(ytdlpDate)
        return "\(String(chars[0..<4]))-\(String(chars[4..<6]))-\(String(chars[6..<8]))"
    }

    /// Adds a video to the photo library; returns a user-facing problem, if any.
    private static func saveToPhotos(_ url: URL) async -> String? {
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else {
            return "Fotoğraflar'a kaydetme izni verilmedi; dosya Dosyalar'da."
        }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                _ = PHAssetCreationRequest.creationRequestForAssetFromVideo(atFileURL: url)
            }
            return nil
        } catch {
            return "Fotoğraflar bu videoyu kabul etmedi; dosya Dosyalar'da."
        }
    }

    // MARK: - Progress

    private func apply(_ event: EngineEvent, to task: DownloadTask) {
        switch event.type {
        case "log":
            if let line = event.line { task.appendLog(line) }
        case "progress":
            guard task.state == .running else { return }
            let parts = max(event.parts ?? 1, 1)
            let part = min(max(event.part ?? 0, 0), parts - 1)
            switch event.role {
            case "video": task.stage = .video
            case "audio": task.stage = task.spec.kind == .audio ? .downloading : .audio
            default: task.stage = .downloading
            }
            var fraction: Double?
            if event.status == "finished" {
                fraction = 1
            } else if let downloaded = event.downloaded, let total = event.total, total > 0 {
                fraction = min(downloaded / total, 1)
            }
            task.speed = event.speed
            task.eta = event.eta
            task.totalBytes = event.total
            if let fraction {
                let itemFraction = (Double(part) + fraction) / Double(parts)
                if task.itemCount > 0 {
                    task.progress = (Double(max(task.itemIndex - 1, 0)) + itemFraction) / Double(task.itemCount)
                } else {
                    task.progress = itemFraction
                }
            }
            syncBackgroundActivity()
        default:
            break
        }
    }

    // MARK: - Library

    private func record(_ task: DownloadTask) {
        guard let modelContext else { return }
        let size = task.outputs.reduce(Int64(0)) { total, url in
            let bytes = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
            return total + Int64(bytes)
        }
        let item = LibraryItem(
            title: task.spec.title,
            sourceURL: task.spec.url,
            thumbnail: task.spec.thumbnail,
            uploader: task.spec.uploader,
            kind: task.spec.kind,
            relativePaths: task.outputs.map(AppPaths.relativePath(of:)),
            sizeBytes: size,
            spec: task.spec
        )
        modelContext.insert(item)
        try? modelContext.save()
    }

    // MARK: - Engine maintenance

    func refreshEngineVersion() async {
        engineVersion = try? await YtdlpEngine.version()
    }

    /// Checks GitHub for a newer yt-dlp. `force` skips the once-a-day limit.
    func updateEngine(force: Bool) async {
        guard !isUpdating else { return }
        if !force {
            guard settings.autoUpdate else { return }
            if let last = settings.lastUpdateCheck, Date().timeIntervalSince(last) < 24 * 3600 { return }
        }
        isUpdating = true
        defer { isUpdating = false }
        do {
            let outcome = try await YtdlpEngine.update(channel: settings.updateChannel)
            settings.lastUpdateCheck = Date()
            switch outcome.status {
            case "updated":
                updateMessage = outcome.restart == true
                    ? "yt-dlp \(outcome.version) indirildi; uygulamayı yeniden açınca etkinleşir."
                    : "yt-dlp \(outcome.version) sürümüne güncellendi."
            default:
                updateMessage = outcome.restart == true
                    ? "yt-dlp \(outcome.version) hazır; uygulamayı yeniden açınca etkinleşir."
                    : "yt-dlp güncel (\(outcome.version))."
            }
        } catch {
            if force {
                updateMessage = "Güncelleme başarısız: \(error.localizedDescription)"
            }
        }
        await refreshEngineVersion()
    }

    func resetEngine() async {
        do {
            let outcome = try await YtdlpEngine.resetToBundled()
            updateMessage = outcome.restart == true
                ? "Paketteki yt-dlp'ye dönülecek; uygulamayı yeniden açın."
                : "Paketteki yt-dlp kullanılıyor."
        } catch {
            updateMessage = "Sıfırlanamadı: \(error.localizedDescription)"
        }
        await refreshEngineVersion()
    }
}
