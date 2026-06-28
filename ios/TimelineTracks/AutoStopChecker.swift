import CoreLocation

// Mirrors the auto-stop logic in Android's LocationService.java.
// Maintains a rolling window of the last 4 accepted fixes.
// Returns true when all 4 are within 100 m of the newest fix (5 points total stationary).
class AutoStopChecker {
    private static let windowSize = 4
    private static let thresholdMeters: Double = 100

    private var recent: [CLLocation] = []

    func reset() {
        recent.removeAll()
    }

    func check(_ newLocation: CLLocation) -> Bool {
        // Build the window first; don't check until it's full.
        guard recent.count == Self.windowSize else {
            recent.append(newLocation)
            return false
        }
        // Check the incoming fix against the existing 4-entry window (5 points total).
        let allNearby = recent.allSatisfy { newLocation.distance(from: $0) <= Self.thresholdMeters }
        // Slide the window: drop oldest, add new fix.
        recent.removeFirst()
        recent.append(newLocation)
        return allNearby
    }

    var recentCount: Int { recent.count }
}
