import Foundation

enum TrackingMode: String {
    case continuous  // GPS chip on at all times; accepts a fix every intervalSeconds
    case periodic    // low-accuracy (cell/Wi-Fi) idle, brief GPS burst every intervalSeconds
}

enum StopReason: String {
    case stopped               // user pressed Stop
    case autoStopped = "auto-stopped"  // stationary detection triggered
}
