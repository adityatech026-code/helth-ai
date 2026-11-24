import Foundation
import Speech
import AVFoundation

protocol SpeechProvider {
    func start(lang: Lang, onPartial: @escaping (String) -> Void, onFinal: @escaping (String) -> Void, onError: @escaping (String) -> Void)
    func stop()
}

/// Apple Speech framework. For en/hi (and others where the device has the on-device pack)
/// `requiresOnDeviceRecognition` makes it work offline. Otherwise it needs a network.
///
/// To use VOSK instead (fully offline for hi/gu/te/en-IN), add the VOSK iOS wrapper
/// (https://github.com/alphacep/vosk-api, ios/ folder) and implement SpeechProvider with it;
/// the rest of the app only depends on this protocol.
final class AppleSpeechProvider: SpeechProvider {
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    func start(lang: Lang, onPartial: @escaping (String) -> Void, onFinal: @escaping (String) -> Void, onError: @escaping (String) -> Void) {
        SFSpeechRecognizer.requestAuthorization { auth in
            DispatchQueue.main.async {
                guard auth == .authorized else { return onError("Speech permission denied") }
                guard let rec = SFSpeechRecognizer(locale: lang.locale), rec.isAvailable else { return onError("\(lang.label) recognition unavailable") }
                do {
                    let session = AVAudioSession.sharedInstance()
                    try session.setCategory(.record, mode: .measurement, options: .duckOthers)
                    try session.setActive(true, options: .notifyOthersOnDeactivation)
                    let req = SFSpeechAudioBufferRecognitionRequest()
                    req.shouldReportPartialResults = true
                    if rec.supportsOnDeviceRecognition { req.requiresOnDeviceRecognition = !Reachability.isOnline }
                    self.request = req
                    let input = self.engine.inputNode
                    input.removeTap(onBus: 0)
                    input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buf, _ in req.append(buf) }
                    self.engine.prepare(); try self.engine.start()
                    self.task = rec.recognitionTask(with: req) { result, error in
                        if let r = result {
                            if r.isFinal { self.stop(); onFinal(r.bestTranscription.formattedString) }
                            else { onPartial(r.bestTranscription.formattedString) }
                        } else if let error { self.stop(); onError(error.localizedDescription) }
                    }
                } catch { onError(error.localizedDescription) }
            }
        }
    }

    func stop() {
        if engine.isRunning { engine.stop(); engine.inputNode.removeTap(onBus: 0) }
        request?.endAudio(); task?.finish(); request = nil
    }
}

import Network
enum Reachability {
    private static let monitor: NWPathMonitor = { let m = NWPathMonitor(); m.start(queue: .global()); return m }()
    static var isOnline: Bool { monitor.currentPath.status == .satisfied }
}
