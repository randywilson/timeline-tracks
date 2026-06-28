import Foundation

// UserDefaults wrapper — mirrors Prefs.java from the Android app.
// TrackingMode and StopReason are defined in LocationTracker.swift;
// Swift compiles the whole module together so the order doesn't matter.
struct Prefs {
    private init() {}
    private static let d = UserDefaults.standard

    static var intervalSeconds: Int {
        get { let v = d.integer(forKey: "interval_seconds"); return v > 0 ? v : 110 }
        set { d.set(newValue, forKey: "interval_seconds") }
    }

    static var autoStop: Bool {
        get { d.object(forKey: "auto_stop") as? Bool ?? true }
        set { d.set(newValue, forKey: "auto_stop") }
    }

    static var trackingMode: TrackingMode {
        get { TrackingMode(rawValue: d.string(forKey: "tracking_mode") ?? "") ?? .continuous }
        set { d.set(newValue.rawValue, forKey: "tracking_mode") }
    }

    static var running: Bool {
        get { d.bool(forKey: "running") }
        set { d.set(newValue, forKey: "running") }
    }

    static var startTime: Date? {
        get { let t = d.double(forKey: "start_time"); return t > 0 ? Date(timeIntervalSince1970: t) : nil }
        set { d.set(newValue?.timeIntervalSince1970 ?? 0, forKey: "start_time") }
    }

    static var stopTime: Date? {
        get { let t = d.double(forKey: "stop_time"); return t > 0 ? Date(timeIntervalSince1970: t) : nil }
        set { d.set(newValue?.timeIntervalSince1970 ?? 0, forKey: "stop_time") }
    }

    static var locationCount: Int {
        get { d.integer(forKey: "location_count") }
        set { d.set(newValue, forKey: "location_count") }
    }

    static var stopReason: StopReason {
        get { StopReason(rawValue: d.string(forKey: "stop_reason") ?? "") ?? .stopped }
        set { d.set(newValue.rawValue, forKey: "stop_reason") }
    }
}
