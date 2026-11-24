import Foundation
import SwiftUI

struct ChatMsg: Identifiable { let id = UUID(); let fromUser: Bool; let text: String; var result: TriageResult? = nil; var care: String? = nil }

@MainActor
final class AppViewModel: ObservableObject {
    private let store = NotebookStore()
    private let speech: SpeechProvider = AppleSpeechProvider()

    @Published var lang: Lang = .hi
    @Published var listening = false
    @Published var partial = ""
    @Published var messages = [ChatMsg(fromUser: false, text: "Namaste! Tell me what you are feeling.")]
    @Published var entries: [Entry] = []
    @Published var trends: [Trend] = []
    @Published var profile = UserProfile()
    private var last: TriageResult?

    init() { refresh() }

    func refresh() { entries = store.all(); trends = Trends.compute(entries) }

    func toggleMic() {
        if listening { speech.stop(); listening = false; return }
        listening = true; partial = ""
        speech.start(lang: lang, onPartial: { [weak self] in self?.partial = $0 },
                     onFinal: { [weak self] in self?.listening = false; self?.partial = ""; self?.handle($0) },
                     onError: { [weak self] e in self?.listening = false; self?.messages.append(ChatMsg(fromUser: false, text: "Sorry, I could not hear that (\(e)). You can also type.")) })
    }

    func handle(_ utterance: String) {
        messages.append(ChatMsg(fromUser: true, text: utterance))
        let intent = Dialogue.detect(utterance)
        if intent != .none { reply(Dialogue.reply(intent, entries: entries, last: last)); return }

        let ex = SymptomExtractor.extract(utterance, lang: lang.rawValue)
        let result = TriageEngine.triage(ex, profile: profile)   // offline, always available
        last = result
        messages.append(ChatMsg(fromUser: false, text: result.headline, result: result))
        Voice.speak(result.headline, lang: lang)
        store.add(Entry(timestamp: Date(), kind: "symptom", lang: lang.rawValue, transcript: utterance, symptoms: ex.symptoms.map(\.id),
                        severity: Dictionary(uniqueKeysWithValues: ex.symptoms.compactMap { s in s.severity.map { (s.id, $0) } }),
                        medications: ex.medications, level: result.level, recommendation: summarize(result)))
        refresh()
        if let h = ex.habits.first, let tip = HabitCoach.tips(h).first { reply(tip) }

        if result.level == "see_doctor" || result.level == "emergency" {
            Task {
                guard let r = await BackendClient.triageWithCare(utterance, lang: lang.rawValue, profile: profile),
                      let care = r["care"] as? [String: Any] else { return }
                var out: [String] = []
                for l in (care["labs"] as? [[String: Any]] ?? []).prefix(2) { out.append("Lab: \(l["name"] ?? "") \(l["phone"] ?? "")") }
                for d in (care["doctors"] as? [[String: Any]] ?? []).prefix(3) { out.append("Dr: \(d["name"] ?? ""), \(d["specialty"] ?? ""), \(d["rating"] ?? "")★ \(d["phone"] ?? "")") }
                if !out.isEmpty { messages.append(ChatMsg(fromUser: false, text: "Nearby options", care: out.joined(separator: "\n"))) }
            }
        }
    }

    private func reply(_ t: String) { messages.append(ChatMsg(fromUser: false, text: t)); Voice.speak(t, lang: lang) }

    func addLab(_ text: String) {
        store.add(Entry(timestamp: Date(), kind: "lab", lang: lang.rawValue, transcript: text, symptoms: [], severity: [:], medications: [], level: "info", recommendation: ""))
        refresh()
    }

    func exportPDF() -> URL { PdfExporter.export(entries: entries, trends: trends) }
    func backup(passphrase: String) throws -> Data { try BackupCrypto.seal(store.exportJSON(), passphrase: passphrase) }
    func restore(_ blob: Data, passphrase: String) throws { store.importJSON(try BackupCrypto.open(blob, passphrase: passphrase)); refresh() }

    private func summarize(_ r: TriageResult) -> String {
        r.level == "self_care"
            ? r.selfCare.map { "\($0.symptom.replacingOccurrences(of: "_", with: " ")): \($0.drug), \($0.dose)" }.joined(separator: "; ")
            : "\(r.level): tests \(r.tests.joined(separator: ", ")); see \(r.specialties.joined(separator: ", "))"
    }
}
