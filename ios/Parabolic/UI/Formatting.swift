import Foundation

enum Format {
    static func bytes(_ value: Double?) -> String? {
        guard let value, value > 0 else { return nil }
        return ByteCountFormatter.string(fromByteCount: Int64(value), countStyle: .file)
    }

    static func speed(_ value: Double?) -> String? {
        guard let text = bytes(value) else { return nil }
        return "\(text)/sn"
    }

    static func remaining(_ seconds: Double?) -> String? {
        guard let seconds, seconds.isFinite, seconds > 0 else { return nil }
        return "\(clock(seconds)) kaldı"
    }

    static func clock(_ seconds: Double) -> String {
        let total = Int(seconds.rounded())
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let secs = total % 60
        if hours > 0 {
            return String(format: "%ld:%02ld:%02ld", hours, minutes, secs)
        }
        return String(format: "%ld:%02ld", minutes, secs)
    }

    static func percent(_ value: Double?) -> String? {
        guard let value else { return nil }
        return "%\(Int((min(max(value, 0), 1) * 100).rounded()))"
    }

    static func relativeDate(_ date: Date) -> String {
        date.formatted(.relative(presentation: .named))
    }
}
