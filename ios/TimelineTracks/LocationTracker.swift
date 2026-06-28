import Foundation
import CoreLocation
import UserNotifications

enum TrackingMode: String {
    case continuous  // GPS chip on at all times; accepts a fix every intervalSeconds
    case periodic    // low-accuracy (cell/Wi-Fi) idle, brief GPS burst every intervalSeconds
}

enum StopReason: String {
    case stopped               // user pressed Stop
    case autoStopped = "auto-stopped"  // stationary detection triggered
}

// Wraps CLLocationManager and drives both tracking modes.
// Publishes state changes to SwiftUI via @Published.
//
// Threading: CLLocationManager is created on the main thread, so all delegate
// callbacks arrive on the main thread. @Published properties are updated directly
// from those callbacks without additional dispatching.
class LocationTracker: NSObject, ObservableObject {

    @Published var isRunning = false
    @Published var locationCount = 0
    @Published var startTime: Date? = nil
    @Published var stopReason: StopReason = .stopped
    @Published var authStatus: CLAuthorizationStatus = .notDetermined
    @Published var accuracyAuth: CLAccuracyAuthorization = .fullAccuracy

    private let manager = CLLocationManager()
    private let autoStopChecker = AutoStopChecker()

    // continuous mode: time of last accepted fix (nil = none yet this session)
    private var lastAcceptedTime: Date? = nil

    // periodic mode
    private var burstTimer: Timer? = nil         // fires at each interval to begin a GPS burst
    private var burstTimeoutTimer: Timer? = nil  // caps the burst at 20 seconds
    private var inBurst = false

    override init() {
        super.init()
        manager.delegate = self
        manager.distanceFilter = kCLDistanceFilterNone
        manager.allowsBackgroundLocationUpdates = true
        manager.pausesLocationUpdatesAutomatically = false
        // Restore state from the previous session
        isRunning = Prefs.running
        locationCount = Prefs.locationCount
        startTime = Prefs.startTime
        stopReason = Prefs.stopReason
        authStatus = manager.authorizationStatus
        accuracyAuth = manager.accuracyAuthorization
        // If the OS killed the app while tracking was active, resume
        if isRunning { resumeTracking() }
    }

    // MARK: - Public API

    var hasRequiredPermissions: Bool {
        authStatus == .authorizedAlways && accuracyAuth == .fullAccuracy
    }

    func requestPermissions() {
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse:
            manager.requestAlwaysAuthorization()
        default:
            break
        }
    }

    func start() {
        guard !isRunning else { return }
        autoStopChecker.reset()
        lastAcceptedTime = nil
        locationCount = 0
        let now = Date()
        startTime = now
        Prefs.locationCount = 0
        Prefs.startTime = now
        Prefs.running = true
        isRunning = true
        activateLocationUpdates()
    }

    func stop(reason: StopReason = .stopped) {
        guard isRunning else { return }
        deactivateLocationUpdates()
        isRunning = false
        stopReason = reason
        Prefs.running = false
        Prefs.stopTime = Date()
        Prefs.stopReason = reason
        if reason == .autoStopped { postAutoStopNotification() }
    }

    func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    // MARK: - Private

    private func resumeTracking() {
        guard hasRequiredPermissions else {
            // Authorization was likely revoked while the app was killed; clear stale state.
            Prefs.running = false
            isRunning = false
            return
        }
        activateLocationUpdates()
    }

    private func activateLocationUpdates() {
        switch Prefs.trackingMode {
        case .continuous:
            manager.desiredAccuracy = kCLLocationAccuracyBest
            manager.startUpdatingLocation()
        case .periodic:
            manager.desiredAccuracy = kCLLocationAccuracyThreeKilometers
            manager.startUpdatingLocation()
            scheduleNextBurst()
        }
    }

    private func deactivateLocationUpdates() {
        manager.stopUpdatingLocation()
        cancelBurstTimer()
        cancelBurstTimeout()
        inBurst = false
    }

    // MARK: - Periodic mode

    private func scheduleNextBurst() {
        cancelBurstTimer()
        burstTimer = Timer.scheduledTimer(
            withTimeInterval: TimeInterval(Prefs.intervalSeconds),
            repeats: false
        ) { [weak self] _ in self?.startBurst() }
    }

    private func startBurst() {
        guard isRunning, !inBurst else { return }
        inBurst = true
        manager.desiredAccuracy = kCLLocationAccuracyBest
        // If no accurate fix arrives within 20 seconds, give up and wait for the next interval.
        burstTimeoutTimer = Timer.scheduledTimer(withTimeInterval: 20, repeats: false) { [weak self] _ in
            self?.endBurst()
        }
    }

    private func endBurst() {
        cancelBurstTimeout()
        inBurst = false
        guard isRunning else { return }
        manager.desiredAccuracy = kCLLocationAccuracyThreeKilometers
        scheduleNextBurst()
    }

    private func cancelBurstTimer() {
        burstTimer?.invalidate()
        burstTimer = nil
    }

    private func cancelBurstTimeout() {
        burstTimeoutTimer?.invalidate()
        burstTimeoutTimer = nil
    }

    // MARK: - Fix acceptance

    private func acceptFix(_ location: CLLocation) {
        locationCount += 1
        Prefs.locationCount = locationCount
        if Prefs.autoStop && autoStopChecker.check(location) {
            stop(reason: .autoStopped)
        }
    }

    // MARK: - Auto-stop notification

    private func postAutoStopNotification() {
        let content = UNMutableNotificationContent()
        content.title = "Timeline Tracks"
        content.body = "Auto-stopped: you appear to be stationary."
        content.sound = .default
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}

// MARK: - CLLocationManagerDelegate

extension LocationTracker: CLLocationManagerDelegate {

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authStatus = manager.authorizationStatus
        accuracyAuth = manager.accuracyAuthorization
        // Automatically escalate from When In Use to Always when the user grants the first step.
        if manager.authorizationStatus == .authorizedWhenInUse {
            manager.requestAlwaysAuthorization()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard isRunning, let location = locations.last else { return }
        let now = Date()

        switch Prefs.trackingMode {
        case .continuous:
            // Rate-limit: only accept one fix per interval.
            if let last = lastAcceptedTime,
               now.timeIntervalSince(last) < TimeInterval(Prefs.intervalSeconds) { return }
            lastAcceptedTime = now
            acceptFix(location)

        case .periodic:
            // Only accept during an active burst, and only if the fix is GPS-accurate.
            guard inBurst else { return }
            let isGpsAccurate = location.horizontalAccuracy > 0 && location.horizontalAccuracy <= 20
            if isGpsAccurate {
                acceptFix(location)
                endBurst()
            }
        }
    }
}
