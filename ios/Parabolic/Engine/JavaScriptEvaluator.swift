import Foundation
import JavaScriptCore

/// Evaluates yt-dlp's EJS challenge-solver programs with JavaScriptCore.
///
/// The program is self-contained (solver library + YouTube's player script)
/// and reports its answer through a single `console.log(JSON.stringify(...))`,
/// exactly like it would under Deno or QuickJS.
enum JavaScriptEvaluator {
    private final class Output: @unchecked Sendable {
        var lines: [String] = []
        var exception: String?
    }

    static func evaluate(_ source: String) -> String {
        guard let context = JSContext() else {
            return encode(["ok": false, "error": "JavaScriptCore bağlamı oluşturulamadı"])
        }
        context.name = "Parabolic challenge solver"
        let output = Output()

        context.exceptionHandler = { _, exception in
            let message = exception?.toString() ?? "bilinmeyen hata"
            let stack = exception?.objectForKeyedSubscript("stack")?.toString()
            output.exception = [message, stack].compactMap { $0 }.joined(separator: "\n")
        }

        let log: @convention(block) () -> Void = {
            let arguments = (JSContext.currentArguments() as? [JSValue]) ?? []
            output.lines.append(arguments.map { $0.toString() ?? "" }.joined(separator: " "))
        }
        let ignore: @convention(block) () -> Void = {}

        if let console = JSValue(newObjectIn: context) {
            console.setObject(unsafeBitCast(log, to: AnyObject.self), forKeyedSubscript: "log" as NSString)
            console.setObject(unsafeBitCast(log, to: AnyObject.self), forKeyedSubscript: "info" as NSString)
            console.setObject(unsafeBitCast(ignore, to: AnyObject.self), forKeyedSubscript: "warn" as NSString)
            console.setObject(unsafeBitCast(ignore, to: AnyObject.self), forKeyedSubscript: "error" as NSString)
            console.setObject(unsafeBitCast(ignore, to: AnyObject.self), forKeyedSubscript: "debug" as NSString)
            context.setObject(console, forKeyedSubscript: "console" as NSString)
        }

        context.evaluateScript(source)

        if let exception = output.exception {
            return encode(["ok": false, "error": exception])
        }
        return encode(["ok": true, "stdout": output.lines.joined(separator: "\n")])
    }

    private static func encode(_ object: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: object) else {
            return #"{"ok": false, "error": "encoding failed"}"#
        }
        return String(decoding: data, as: UTF8.self)
    }
}
