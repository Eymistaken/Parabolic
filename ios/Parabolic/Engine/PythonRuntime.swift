import Foundation

/// Owns the embedded CPython interpreter that runs yt-dlp.
///
/// Every call runs on its own thread with a generous stack: yt-dlp blocks for
/// the whole length of a download, which must never happen on Swift's
/// cooperative thread pool, and Python's recursion guard sizes itself from the
/// thread's real stack.
final class PythonRuntime: @unchecked Sendable {
    static let shared = PythonRuntime()

    private let lock = NSLock()
    private var started = false
    private var startFailure: String?

    private init() {}

    private func startIfNeeded() throws {
        lock.lock()
        defer { lock.unlock() }
        if started { return }
        if let startFailure { throw EngineError.engineUnavailable(startFailure) }

        guard let resourcePath = Bundle.main.resourcePath else {
            startFailure = "Uygulama paketi okunamadı"
            throw EngineError.engineUnavailable(startFailure!)
        }
        setenv("PARABOLIC_DATA_DIR", AppPaths.engineData.path, 1)
        setenv("PARABOLIC_CACHE_DIR", AppPaths.engineCache.path, 1)
        setenv("NO_COLOR", "1", 1)
        setenv("PYTHON_COLORS", "0", 1)

        var errorPointer: UnsafeMutablePointer<CChar>?
        let status = parabolic_python_start(resourcePath, pythonEmitCallback, pythonEvalJSCallback, &errorPointer)
        if status != 0 {
            let message = errorPointer.map { String(cString: $0) } ?? "Python başlatılamadı"
            parabolic_free(errorPointer)
            startFailure = message
            throw EngineError.engineUnavailable(message)
        }
        started = true
    }

    /// Sends one request to `parabolic_bridge.handle` and returns the raw JSON reply.
    func call(_ request: [String: Any]) async throws -> Data {
        let payload = try JSONSerialization.data(withJSONObject: request)
        let json = String(decoding: payload, as: UTF8.self)
        return try await LargeStackThread.run { () throws -> Data in
            try self.startIfNeeded()
            guard let reply = parabolic_python_call(json) else {
                throw EngineError.bridge("Python yanıt vermedi")
            }
            defer { parabolic_free(reply) }
            return Data(String(cString: reply).utf8)
        }
    }
}

/// Runs blocking work on a dedicated thread and resumes the caller afterwards.
enum LargeStackThread {
    static let stackSize = 16 * 1024 * 1024

    static func run<T>(_ body: @escaping () throws -> T) async throws -> T {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<T, Error>) in
            let thread = Thread {
                do {
                    continuation.resume(returning: try body())
                } catch {
                    continuation.resume(throwing: error)
                }
            }
            thread.stackSize = stackSize
            thread.qualityOfService = .userInitiated
            thread.start()
        }
    }

    /// Synchronous variant for callers that already sit on a background thread.
    static func runAndWait<T>(_ body: @escaping () -> T) -> T {
        let semaphore = DispatchSemaphore(value: 0)
        let box = ResultBox<T>()
        let thread = Thread {
            box.value = body()
            semaphore.signal()
        }
        thread.stackSize = stackSize
        thread.qualityOfService = .userInitiated
        thread.start()
        semaphore.wait()
        return box.value!
    }

    private final class ResultBox<T>: @unchecked Sendable {
        var value: T?
    }
}

// MARK: - C callbacks

/// Python → Swift: progress and log events, as JSON.
private let pythonEmitCallback: parabolic_emit_fn = { pointer in
    guard let pointer else { return }
    let data = Data(String(cString: pointer).utf8)
    EngineEvents.shared.deliver(data)
}

/// Python → Swift: run a YouTube challenge-solver program in JavaScriptCore.
private let pythonEvalJSCallback: parabolic_eval_js_fn = { pointer in
    let source = pointer.map { String(cString: $0) } ?? ""
    let reply = LargeStackThread.runAndWait { JavaScriptEvaluator.evaluate(source) }
    return strdup(reply)
}
