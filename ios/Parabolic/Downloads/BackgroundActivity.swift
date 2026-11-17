import BackgroundTasks
import Foundation
import UIKit
import UserNotifications

/// Keeps downloads alive when the user leaves the app.
///
/// iOS 26's continued-processing tasks are made for exactly this: a task the
/// user started in the foreground keeps running in the background, with the
/// system showing its progress. A short UIKit background task covers the
/// moment between leaving the app and the system picking the task up.
@MainActor
final class BackgroundActivity {
    private var continuedTask: BGContinuedProcessingTask?
    private var submittedIdentifier: String?
    private var uiTask: UIBackgroundTaskIdentifier = .invalid
    private var title = "Parabolic"
    private var subtitle = ""
    private var fraction = 0.0

    /// `<bundle id>.downloads.*` from Info.plist, with the wildcard filled in.
    private var newIdentifier: String? {
        let permitted = Bundle.main.object(forInfoDictionaryKey: "BGTaskSchedulerPermittedIdentifiers") as? [String]
        guard let template = permitted?.first(where: { $0.hasSuffix(".*") }) else { return nil }
        return String(template.dropLast()) + UUID().uuidString
    }

    var isActive: Bool { submittedIdentifier != nil || uiTask != .invalid }

    func begin(title: String, subtitle: String) {
        self.title = title
        self.subtitle = subtitle

        if uiTask == .invalid {
            uiTask = UIApplication.shared.beginBackgroundTask(withName: "Parabolic indirmeleri") { [weak self] in
                Task { @MainActor in self?.endUIKitTask() }
            }
        }

        guard submittedIdentifier == nil, let identifier = newIdentifier else { return }
        let registered = BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: .main) { [weak self] task in
            MainActor.assumeIsolated {
                guard let self, let continued = task as? BGContinuedProcessingTask else {
                    task.setTaskCompleted(success: false)
                    return
                }
                self.attach(continued)
            }
        }
        guard registered else { return }

        let request = BGContinuedProcessingTaskRequest(identifier: identifier, title: title, subtitle: subtitle)
        request.strategy = .fail
        do {
            try BGTaskScheduler.shared.submit(request)
            submittedIdentifier = identifier
        } catch {
            submittedIdentifier = nil
        }
    }

    func update(fraction: Double, title: String, subtitle: String) {
        self.fraction = min(max(fraction, 0), 1)
        self.title = title
        self.subtitle = subtitle
        guard let continuedTask else { return }
        continuedTask.progress.completedUnitCount = Int64(self.fraction * 1000)
        continuedTask.updateTitle(title, subtitle: subtitle)
    }

    func end(success: Bool) {
        continuedTask?.progress.completedUnitCount = 1000
        continuedTask?.setTaskCompleted(success: success)
        continuedTask = nil
        submittedIdentifier = nil
        endUIKitTask()
    }

    private func attach(_ task: BGContinuedProcessingTask) {
        continuedTask = task
        task.progress.totalUnitCount = 1000
        task.progress.completedUnitCount = Int64(fraction * 1000)
        task.updateTitle(title, subtitle: subtitle)
        task.expirationHandler = { [weak self] in
            Task { @MainActor in
                self?.continuedTask = nil
                self?.submittedIdentifier = nil
            }
        }
    }

    private func endUIKitTask() {
        guard uiTask != .invalid else { return }
        UIApplication.shared.endBackgroundTask(uiTask)
        uiTask = .invalid
    }
}

enum Notifier {
    static func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    /// Posts a local notification, but only while the app is not on screen.
    @MainActor
    static func post(title: String, body: String) {
        guard UIApplication.shared.applicationState != .active else { return }
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}
