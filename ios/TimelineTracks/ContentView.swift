import SwiftUI
import UIKit

struct ContentView: View {
    @StateObject private var tracker = LocationTracker()
    @State private var intervalText = String(Prefs.intervalSeconds)
    @State private var autoStop = Prefs.autoStop
    @State private var trackingMode = Prefs.trackingMode
    @State private var showIntervalInfo = false
    @State private var showAutoStopInfo = false
    @State private var showModeInfo = false
    @State private var showAbout = false
    @State private var showHowItWorks = false
    @FocusState private var intervalFocused: Bool
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 0) {
            titleBar
            ScrollView {
                VStack(spacing: 0) {
                    permissionBanner
                    mainCard
                        .padding(16)
                }
            }
            .background(Color.ttBrown)
        }
        .ignoresSafeArea(edges: .top)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { intervalFocused = false }
            }
        }
        .onAppear { tracker.requestPermissions() }
    }

    // MARK: - Title bar

    private var titleBar: some View {
        HStack(spacing: 10) {
            Image(systemName: "location.fill")
                .font(.system(size: 18))
                .foregroundColor(.white)
            Text("Timeline Tracks")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.white)
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 14)
        .background(Color.ttSkyBlue.ignoresSafeArea(edges: .top))
    }

    // MARK: - Permission banner

    @ViewBuilder
    private var permissionBanner: some View {
        if !tracker.hasRequiredPermissions {
            VStack(spacing: 10) {
                Text(permissionMessage)
                    .font(.system(size: 14))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                Button("Give Permission") { onGivePermission() }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Color.ttNearBlack)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                    .background(Color.white)
                    .cornerRadius(4)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Color(red: 0xB7/255, green: 0x1C/255, blue: 0x1C/255))
        }
    }

    private var permissionMessage: String {
        if tracker.authStatus == .denied || tracker.authStatus == .restricted {
            return "Location permission denied. Please enable it in Settings. This app stores no data and has no internet permission."
        }
        if tracker.authStatus == .authorizedWhenInUse {
            return "Please set location access to \"Always\" in Settings so Timeline Tracks can run while the screen is off."
        }
        if tracker.accuracyAuth == .reducedAccuracy {
            return "Please enable Precise Location in Settings. Accurate GPS is required for auto-stop."
        }
        return "Location permission is required. This app stores no data and has no internet permission."
    }

    private func onGivePermission() {
        switch tracker.authStatus {
        case .notDetermined, .authorizedWhenInUse:
            tracker.requestPermissions()
        default:
            if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
        }
    }

    // MARK: - Main card

    private var mainCard: some View {
        VStack(spacing: 16) {
            intervalRow
            Divider()
            autoStopRow
            Divider()
            modeRow
            Divider()
            startStopButton
            statsRow
            Divider()
            footerLinks
        }
        .padding(16)
        .background(Color.ttOffWhite)
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
    }

    // MARK: - Card rows

    private var intervalRow: some View {
        HStack {
            Text("Interval (seconds)")
                .font(.system(size: 16))
                .foregroundColor(Color.ttNearBlack)
            Spacer()
            TextField("110", text: $intervalText)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 72)
                .focused($intervalFocused)
                .onChange(of: intervalText) { _, newValue in
                    let digits = String(newValue.prefix(6).filter(\.isNumber))
                    if digits != newValue { intervalText = digits }
                    if let v = Int(digits), v > 0 { Prefs.intervalSeconds = v }
                }
            infoButton(isPresented: $showIntervalInfo)
        }
        .alert("Interval", isPresented: $showIntervalInfo) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("How often to request a GPS fix, in seconds. Shorter intervals are more accurate but use more battery. Default is 110 seconds.")
        }
    }

    private var autoStopRow: some View {
        HStack {
            Text("Auto-stop when stationary")
                .font(.system(size: 16))
                .foregroundColor(Color.ttNearBlack)
            Spacer()
            Toggle("", isOn: $autoStop)
                .labelsHidden()
                .onChange(of: autoStop) { _, newValue in Prefs.autoStop = newValue }
            infoButton(isPresented: $showAutoStopInfo)
        }
        .alert("Auto-stop", isPresented: $showAutoStopInfo) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Automatically stops tracking when you appear to be stationary — when 5 consecutive GPS fixes are all within 100 m of each other.")
        }
    }

    private var modeRow: some View {
        HStack {
            Text("Mode")
                .font(.system(size: 16))
                .foregroundColor(Color.ttNearBlack)
            Spacer()
            Picker("Mode", selection: $trackingMode) {
                Text("Reliable").tag(TrackingMode.continuous)
                Text("Battery Saver").tag(TrackingMode.periodic)
            }
            .pickerStyle(.segmented)
            .frame(width: 190)
            .disabled(tracker.isRunning)
            .onChange(of: trackingMode) { _, newValue in Prefs.trackingMode = newValue }
            infoButton(isPresented: $showModeInfo)
        }
        .alert("Tracking Mode", isPresented: $showModeInfo) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Reliable: GPS runs continuously. Higher battery use, but always gives other apps a fresh fix.\n\nBattery Saver: Stays in low-power (cell/Wi-Fi) mode between intervals, then briefly tries to get a GPS fix. Uses less battery, but the GPS burst is best-effort — it may not always succeed or benefit other apps.")
        }
    }

    private var startStopButton: some View {
        Button(action: onStartStop) {
            Text(tracker.isRunning ? "Stop" : "Start")
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(tracker.isRunning ? Color.ttStopRed : Color.ttStartGreen)
                .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var statsRow: some View {
        if tracker.isRunning || tracker.locationCount > 0 {
            TimelineView(.periodic(from: .now, by: 1.0)) { _ in
                Text(statsText)
                    .font(.system(size: 14))
                    .foregroundColor(Color.ttNearBlack)
                    .multilineTextAlignment(.center)
            }
        }
    }

    private var footerLinks: some View {
        HStack(spacing: 8) {
            Button("About") { showAbout = true }
                .font(.system(size: 14))
            Text("·").foregroundColor(Color.ttNearBlack)
            Button("How it works") { showHowItWorks = true }
                .font(.system(size: 14))
        }
        .alert("About", isPresented: $showAbout) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Timeline Tracks\nOpen source, GPL-3.0\ngithub.com/randywilson/timeline-tracks\n\nNot affiliated with Google LLC.")
        }
        .alert("How it works", isPresented: $showHowItWorks) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("iOS shares location hardware across apps. When Timeline Tracks requests high-accuracy GPS, the chip stays active and other apps — like Google Timeline — receive timely, accurate fixes even on hikes and boat trips where you're away from Wi-Fi.")
        }
    }

    // MARK: - Helpers

    private func infoButton(isPresented: Binding<Bool>) -> some View {
        Button { isPresented.wrappedValue = true } label: {
            Image(systemName: "info.circle")
                .foregroundColor(Color.ttNearBlack.opacity(0.5))
                .font(.system(size: 16))
        }
        .buttonStyle(.plain)
    }

    private func onStartStop() {
        if tracker.isRunning {
            tracker.stop()
        } else if tracker.hasRequiredPermissions {
            tracker.requestNotificationPermission()
            tracker.start()
        } else {
            tracker.requestPermissions()
        }
    }

    private var statsText: String {
        var parts: [String] = []
        if let start = tracker.startTime {
            let end = tracker.isRunning ? Date() : (Prefs.stopTime ?? Date())
            parts.append(formatDuration(end.timeIntervalSince(start)))
        }
        let n = tracker.locationCount
        parts.append("\(n) \(n == 1 ? "fix" : "fixes")")
        parts.append(tracker.isRunning ? "running" : tracker.stopReason.rawValue)
        return parts.joined(separator: " · ")
    }

    private func formatDuration(_ seconds: TimeInterval) -> String {
        let s = Int(max(0, seconds))
        let h = s / 3600
        let m = (s % 3600) / 60
        let sec = s % 60
        return h > 0
            ? String(format: "%d:%02d:%02d", h, m, sec)
            : String(format: "%d:%02d", m, sec)
    }
}
