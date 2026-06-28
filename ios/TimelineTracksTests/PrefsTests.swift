import XCTest
@testable import TimelineTracks

final class PrefsTests: XCTestCase {
    private let allKeys = [
        "interval_seconds", "auto_stop", "tracking_mode",
        "running", "start_time", "stop_time", "location_count", "stop_reason"
    ]

    override func setUp() {
        super.setUp()
        allKeys.forEach { UserDefaults.standard.removeObject(forKey: $0) }
    }

    override func tearDown() {
        allKeys.forEach { UserDefaults.standard.removeObject(forKey: $0) }
        super.tearDown()
    }

    // MARK: - intervalSeconds

    func testIntervalSecondsDefaultIs110() {
        XCTAssertEqual(Prefs.intervalSeconds, 110)
    }

    func testIntervalSecondsRoundTrip() {
        Prefs.intervalSeconds = 60
        XCTAssertEqual(Prefs.intervalSeconds, 60)
    }

    func testIntervalSecondsZeroFallsBackToDefault() {
        // Storing 0 is treated as "unset" — getter returns 110
        UserDefaults.standard.set(0, forKey: "interval_seconds")
        XCTAssertEqual(Prefs.intervalSeconds, 110)
    }

    // MARK: - autoStop

    func testAutoStopDefaultIsTrue() {
        XCTAssertTrue(Prefs.autoStop)
    }

    func testAutoStopRoundTrip() {
        Prefs.autoStop = false
        XCTAssertFalse(Prefs.autoStop)
        Prefs.autoStop = true
        XCTAssertTrue(Prefs.autoStop)
    }

    // MARK: - trackingMode

    func testTrackingModeDefaultIsContinuous() {
        XCTAssertEqual(Prefs.trackingMode, .continuous)
    }

    func testTrackingModeRoundTrip() {
        Prefs.trackingMode = .periodic
        XCTAssertEqual(Prefs.trackingMode, .periodic)
        Prefs.trackingMode = .continuous
        XCTAssertEqual(Prefs.trackingMode, .continuous)
    }

    func testTrackingModeUnknownStringFallsBackToContinuous() {
        UserDefaults.standard.set("bogus", forKey: "tracking_mode")
        XCTAssertEqual(Prefs.trackingMode, .continuous)
    }

    // MARK: - running

    func testRunningDefaultIsFalse() {
        XCTAssertFalse(Prefs.running)
    }

    func testRunningRoundTrip() {
        Prefs.running = true
        XCTAssertTrue(Prefs.running)
        Prefs.running = false
        XCTAssertFalse(Prefs.running)
    }

    // MARK: - startTime

    func testStartTimeDefaultIsNil() {
        XCTAssertNil(Prefs.startTime)
    }

    func testStartTimeRoundTrip() {
        let date = Date(timeIntervalSince1970: 1_700_000_000)
        Prefs.startTime = date
        XCTAssertEqual(
            Prefs.startTime?.timeIntervalSince1970,
            date.timeIntervalSince1970,
            accuracy: 0.001
        )
    }

    func testStartTimeNilRoundTrip() {
        Prefs.startTime = Date()
        Prefs.startTime = nil
        XCTAssertNil(Prefs.startTime)
    }

    // MARK: - stopTime

    func testStopTimeDefaultIsNil() {
        XCTAssertNil(Prefs.stopTime)
    }

    func testStopTimeRoundTrip() {
        let date = Date(timeIntervalSince1970: 1_700_001_000)
        Prefs.stopTime = date
        XCTAssertEqual(
            Prefs.stopTime?.timeIntervalSince1970,
            date.timeIntervalSince1970,
            accuracy: 0.001
        )
    }

    // MARK: - locationCount

    func testLocationCountDefaultIsZero() {
        XCTAssertEqual(Prefs.locationCount, 0)
    }

    func testLocationCountRoundTrip() {
        Prefs.locationCount = 42
        XCTAssertEqual(Prefs.locationCount, 42)
    }

    // MARK: - stopReason

    func testStopReasonDefaultIsStopped() {
        XCTAssertEqual(Prefs.stopReason, .stopped)
    }

    func testStopReasonRoundTrip() {
        Prefs.stopReason = .autoStopped
        XCTAssertEqual(Prefs.stopReason, .autoStopped)
        Prefs.stopReason = .stopped
        XCTAssertEqual(Prefs.stopReason, .stopped)
    }

    func testStopReasonRawValueMatchesAndroid() {
        // The raw string values must match what Android's LocationService writes
        // if we ever share preferences across platforms (e.g. via iCloud sync).
        XCTAssertEqual(StopReason.stopped.rawValue, "stopped")
        XCTAssertEqual(StopReason.autoStopped.rawValue, "auto-stopped")
    }

    func testStopReasonUnknownStringFallsBackToStopped() {
        UserDefaults.standard.set("bogus", forKey: "stop_reason")
        XCTAssertEqual(Prefs.stopReason, .stopped)
    }
}
