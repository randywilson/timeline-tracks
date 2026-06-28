import XCTest
import CoreLocation
@testable import TimelineTracks

final class AutoStopCheckerTests: XCTestCase {
    var checker: AutoStopChecker!

    override func setUp() {
        super.setUp()
        checker = AutoStopChecker()
    }

    // MARK: - Deque growth

    func testRecentCountStartsAtZero() {
        XCTAssertEqual(checker.recentCount, 0)
    }

    func testRecentCountGrowsOnePerCheck() {
        for i in 0..<4 {
            _ = checker.check(moving(i))
            XCTAssertEqual(checker.recentCount, i + 1)
        }
    }

    func testRecentCountDoesNotExceedWindowSize() {
        for i in 0..<10 {
            _ = checker.check(moving(i))
        }
        XCTAssertEqual(checker.recentCount, 4)
    }

    // MARK: - Trigger point

    func testFourStationaryFixesDoNotTrigger() {
        // Window is still filling — need 5 total (4 in window + 1 new)
        for _ in 0..<4 {
            XCTAssertFalse(checker.check(loc(0, 0)))
        }
    }

    func testFifthStationaryFixTriggers() {
        for _ in 0..<4 {
            _ = checker.check(loc(0, 0))
        }
        XCTAssertTrue(checker.check(loc(0, 0)))
    }

    func testSixthAndBeyondAlsoTriggerWhenStationary() {
        for _ in 0..<4 {
            _ = checker.check(loc(0, 0))
        }
        XCTAssertTrue(checker.check(loc(0, 0)))
        XCTAssertTrue(checker.check(loc(0, 0)))
    }

    // MARK: - Moving scenario

    func testMovingLocationsNeverTrigger() {
        // Each fix is ~111 m apart — well beyond the 100 m threshold
        for i in 0..<10 {
            XCTAssertFalse(checker.check(moving(i)))
        }
    }

    // MARK: - Stationary scenario

    func testSlightlyScatteredStationaryFixesTrigger() {
        // Fixes within ~16 m of each other — well inside 100 m
        _ = checker.check(loc(0.0000, 0.0000))   // ~  0 m from origin
        _ = checker.check(loc(0.0001, 0.0000))   // ~11 m
        _ = checker.check(loc(0.0000, 0.0001))   // ~11 m
        _ = checker.check(loc(0.0001, 0.0001))   // ~16 m diagonal
        XCTAssertTrue(checker.check(loc(0.00005, 0.00005)))  // ~8 m from origin
    }

    // MARK: - One distant fix breaks a stationary run

    func testDistantFixPreventsImmediateTrigger() {
        // Fill window with stationary fixes
        for _ in 0..<4 {
            _ = checker.check(loc(0, 0))
        }
        // One distant fix slides into the window (~222 m away)
        _ = checker.check(loc(0.002, 0))
        // Next stationary fix is 222 m from the distant one — should not trigger
        XCTAssertFalse(checker.check(loc(0, 0)))
    }

    func testRecoversTriggerAfterDistantFixScrollsOut() {
        // Fill window with stationary fixes, inject one distant fix, then go stationary again
        for _ in 0..<4 {
            _ = checker.check(loc(0, 0))
        }
        _ = checker.check(loc(0.002, 0))  // distant fix now in window
        _ = checker.check(loc(0, 0))      // distant fix still in window (3rd slot)
        _ = checker.check(loc(0, 0))      // distant fix still in window (2nd slot)
        _ = checker.check(loc(0, 0))      // distant fix slides out; window is all stationary again
        XCTAssertTrue(checker.check(loc(0, 0)))  // 5th stationary since distant fix left
    }

    // MARK: - Reset

    func testResetClearsCount() {
        for _ in 0..<4 { _ = checker.check(loc(0, 0)) }
        checker.reset()
        XCTAssertEqual(checker.recentCount, 0)
    }

    func testResetRequiresFiveMoreFixesToTrigger() {
        for _ in 0..<4 { _ = checker.check(loc(0, 0)) }
        checker.reset()
        for _ in 0..<4 {
            XCTAssertFalse(checker.check(loc(0, 0)))
        }
        XCTAssertTrue(checker.check(loc(0, 0)))
    }

    // MARK: - Helpers

    /// A fix at the given 0-based index, each ~111 m apart.
    private func moving(_ index: Int) -> CLLocation {
        loc(Double(index) * 0.001, 0)
    }

    private func loc(_ lat: Double, _ lon: Double) -> CLLocation {
        CLLocation(latitude: lat, longitude: lon)
    }
}
