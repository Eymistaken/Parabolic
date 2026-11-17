import Foundation
import UIKit

enum Artwork {
    /// Downloads a thumbnail and returns it as JPEG, optionally cropped to a
    /// centered square: YouTube Music uploads put the album cover in the middle
    /// of a 16:9 frame, so the square crop recovers the real cover.
    static func load(from urlString: String?, square: Bool) async -> Data? {
        guard let urlString, let url = URL(string: urlString) else { return nil }
        guard let fetched = try? await URLSession.shared.data(from: url),
              (fetched.1 as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) ?? true,
              let image = UIImage(data: fetched.0),
              let cgImage = image.cgImage
        else { return nil }

        var output = cgImage
        if square {
            let side = min(cgImage.width, cgImage.height)
            let rect = CGRect(
                x: (cgImage.width - side) / 2,
                y: (cgImage.height - side) / 2,
                width: side,
                height: side
            )
            if let cropped = cgImage.cropping(to: rect) {
                output = cropped
            }
        }
        return UIImage(cgImage: output).jpegData(compressionQuality: 0.9)
    }
}

enum SponsorBlock {
    private struct Segment: Decodable {
        let segment: [Double]
        let actionType: String?
    }

    static let categories = ["sponsor", "selfpromo", "interaction"]

    /// Community-submitted sponsor segments for a YouTube video, in seconds.
    static func segments(for videoID: String) async -> [ClosedRange<Double>] {
        var components = URLComponents(string: "https://sponsor.ajay.app/api/skipSegments")!
        let categoryList = "[" + categories.map { "\"\($0)\"" }.joined(separator: ",") + "]"
        components.queryItems = [
            URLQueryItem(name: "videoID", value: videoID),
            URLQueryItem(name: "categories", value: categoryList),
        ]
        guard let url = components.url,
              let fetched = try? await URLSession.shared.data(from: url),
              (fetched.1 as? HTTPURLResponse)?.statusCode == 200,
              let segments = try? JSONDecoder().decode([Segment].self, from: fetched.0)
        else { return [] }

        return segments.compactMap { segment in
            guard segment.actionType == nil || segment.actionType == "skip",
                  segment.segment.count == 2,
                  segment.segment[1] > segment.segment[0]
            else { return nil }
            return segment.segment[0]...segment.segment[1]
        }
    }
}
