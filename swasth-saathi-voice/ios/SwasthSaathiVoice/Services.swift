import Foundation
import UIKit
import UserNotifications
import AVFoundation

// MARK: - Trends (Progress view)
struct Trend { let symptom: String; let last30: Int; let prev30: Int; let weekly: [Int]; let message: String }

enum Trends {
    static func compute(_ entries: [Entry], now: Date = Date()) -> [Trend] {
        let day: TimeInterval = 86_400
        var times: [String: [Date]] = [:]
        for e in entries where e.kind == "symptom" { for s in e.symptoms { times[s, default: []].append(e.timestamp) } }
        return times.map { sym, ts in
            let age = ts.map { now.timeIntervalSince($0) }
            let recent = age.filter { $0 <= 30 * day }.count
            let prev = age.filter { $0 > 30 * day && $0 <= 60 * day }.count
            let weekly = (0..<4).map { w in age.filter { $0 >= Double(w) * 7 * day && $0 < Double(w + 1) * 7 * day }.count }.reversed()
            let label = sym.replacingOccurrences(of: "_", with: " ")
            let msg: String
            if recent >= 4 { msg = "You mentioned \(label) \(recent) times in 30 days. Recurring symptoms are worth showing a doctor." }
            else if recent > prev && prev > 0 { msg = "\(label) is more frequent than last month (\(recent) vs \(prev))." }
            else if recent < prev { msg = "\(label) is improving (\(recent) vs \(prev) last month)." }
            else { msg = "\(label): \(recent) time(s) in the last 30 days." }
            return Trend(symptom: sym, last30: recent, prev30: prev, weekly: Array(weekly), message: msg)
        }.sorted { $0.last30 > $1.last30 }
    }
}

// MARK: - Dialogue (follow-up questions)
enum Dialogue {
    enum Intent { case nextStep, lastResults, progress, repeatLast, book, none }
    private static let words: [(Intent, [String])] = [
        (.nextStep, ["what should i do", "what next", "next step", "अब क्या करूं", "अब क्या करें", "आगे क्या", "এরপর কী", "এখন কী করব", "அடுத்து என்ன", "இப்போது என்ன செய்ய", "पुढे काय", "आता काय करू", "ఇప్పుడు ఏం చేయాలి", "తర్వాత ఏమిటి", "હવે શું કરું", "આગળ શું"]),
        (.lastResults, ["last blood", "blood test result", "my results", "last report", "मेरी रिपोर्ट", "ब्लड टेस्ट", "রক্ত পরীক্ষা", "রিপোর্ট", "இரத்த பரிசோதனை", "रक्त तपासणी", "రక్త పరీక్ష", "રક્ત તપાસ"]),
        (.progress, ["progress", "how am i doing", "trend", "प्रगति", "অগ্রগতি", "முன்னேற்றம்", "प्रगती", "పురోగతి", "પ્રગતિ"]),
        (.repeatLast, ["repeat", "say again", "फिर से बोलो", "दोबारा बोलो", "আবার বলুন", "மீண்டும் சொல்", "पुन्हा सांगा", "మళ్లీ చెప్పు", "ફરી કહો"]),
        (.book, ["book", "appointment", "बुक", "अपॉइंटमेंट", "অ্যাপয়েন্টমেন্ট", "అపాయింట్‌మెంట్", "એપોઇન્ટમેન્ટ"])
    ]
    static func detect(_ u: String) -> Intent {
        let t = SymptomExtractor.normalize(u)
        return words.first { $0.1.contains { t.contains($0.lowercased()) } }?.0 ?? .none
    }
    static func reply(_ i: Intent, entries: [Entry], last: TriageResult?) -> String {
        switch i {
        case .nextStep:
            switch last?.level {
            case "emergency": return "Please call 112 or 108 now, or have someone take you to the nearest hospital."
            case "see_doctor": return "Get \(last!.tests.prefix(2).joined(separator: " and ")) done \(last!.urgency == "within_24h" ? "today or tomorrow" : "in the next few days"), then see a doctor. Want me to show nearby labs?"
            case "self_care": return "Rest, drink fluids and follow the dose I gave. If it is not better in 2 days, tell me and we will arrange tests."
            default: return "Tell me what you are feeling and I will guide you."
            }
        case .lastResults:
            if let l = entries.first(where: { $0.kind == "lab" }) { return "Your last report: \(l.transcript). Saved on \(l.timestamp.formatted(.dateTime.day().month()))." }
            return "I do not have a lab report saved yet. You can add one from the Notebook screen."
        case .progress: return Trends.compute(entries).first?.message ?? "Nothing recurring so far. Keep logging how you feel."
        case .repeatLast: return last?.headline ?? "I have nothing to repeat yet."
        case .book: return "Opening doctors near you. Please confirm a time slot."
        case .none: return ""
        }
    }
}

// MARK: - Habit coach
enum HabitCoach {
    static func tips(_ habit: String) -> [String] {
        ((KB.root["habits"] as? [String: [String: Any]])?[habit]?["tips"] as? [String]) ?? []
    }
    static func weeklyMealPlan() -> [(String, String)] {
        let plan = (KB.root["mealPlans"] as? [String: [String: String]])?["default"] ?? [:]
        return ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].compactMap { d in plan[d].map { (d, $0) } }
    }
    /// Gentle daily reminder as a local notification (works offline). Voice playback happens when the user opens it.
    static func scheduleDaily(habit: String, hour: Int = 9) {
        guard let tip = tips(habit).randomElement() else { return }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { ok, _ in
            guard ok else { return }
            let c = UNMutableNotificationContent(); c.title = "Swasth Saathi"; c.body = tip; c.sound = .default
            var d = DateComponents(); d.hour = hour
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "habit-\(habit)", content: c,
                trigger: UNCalendarNotificationTrigger(dateMatching: d, repeats: true)))
        }
    }
}

enum Voice {
    private static let synth = AVSpeechSynthesizer()
    static func speak(_ text: String, lang: Lang) {
        let u = AVSpeechUtterance(string: text); u.voice = AVSpeechSynthesisVoice(language: lang.locale.identifier); u.rate = 0.45
        synth.speak(u)
    }
}

// MARK: - PDF export
enum PdfExporter {
    static func export(entries: [Entry], trends: [Trend], patient: String? = nil) -> URL {
        let page = CGRect(x: 0, y: 0, width: 595, height: 842), margin: CGFloat = 36
        let df = DateFormatter(); df.dateFormat = "dd MMM yyyy, HH:mm"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("swasth_saathi_notebook.pdf")
        let body: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 10)]
        let bold: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 10)]
        let head: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 15)]
        var lines: [(String, [NSAttributedString.Key: Any])] = [
            ("Swasth Saathi Voice - Health Notebook", head),
            ("Patient: \(patient ?? "-")    Generated: \(df.string(from: Date()))", body),
            ("Self-reported data from a voice assistant. Not a medical record or diagnosis.", body), ("", body), ("Trends", head)
        ]
        lines += trends.isEmpty ? [("No recurring symptoms yet.", body)] : trends.map { ("- " + $0.message, body) }
        lines += [("", body), ("Log (newest first)", head)]
        for e in entries {
            lines.append(("\(df.string(from: e.timestamp))  [\(e.level)]", bold))
            lines.append(("Said: \(e.transcript)", body))
            let syms = e.symptoms.map { s in s.replacingOccurrences(of: "_", with: " ") + (e.severity[s].map { " (\($0))" } ?? "") }.joined(separator: ", ")
            lines.append(("Symptoms: \(syms.isEmpty ? "-" : syms)" + (e.medications.isEmpty ? "" : "   Medication mentioned: \(e.medications.joined(separator: ", "))"), body))
            lines.append(("Advice given: \(e.recommendation)", body)); lines.append(("", body))
        }
        try? UIGraphicsPDFRenderer(bounds: page).writePDF(to: url) { ctx in
            var y = margin; ctx.beginPage()
            for (text, attrs) in lines {
                let s = NSAttributedString(string: text.isEmpty ? " " : text, attributes: attrs)
                let r = s.boundingRect(with: CGSize(width: page.width - 2 * margin, height: .greatestFiniteMagnitude), options: .usesLineFragmentOrigin, context: nil)
                if y + r.height > page.height - margin { ctx.beginPage(); y = margin }
                s.draw(in: CGRect(x: margin, y: y, width: page.width - 2 * margin, height: r.height)); y += r.height + 4
            }
        }
        return url
    }
}

// MARK: - Backend client (optional; app works offline without it)
enum BackendClient {
    static var base: URL { URL(string: Bundle.main.object(forInfoDictionaryKey: "BackendURL") as? String ?? "http://localhost:3000")! }

    private static func post(_ path: String, _ body: [String: Any]) async throws -> [String: Any] {
        var req = URLRequest(url: base.appendingPathComponent(path)); req.httpMethod = "POST"; req.timeoutInterval = 6
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        // req.setValue(apiKey, forHTTPHeaderField: "x-api-key")  // inject from secure config, never hard-code
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, _) = try await URLSession.shared.data(for: req)
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }
    static func triageWithCare(_ text: String, lang: String, profile: UserProfile) async -> [String: Any]? {
        try? await post("/v1/triage", ["transcript": text, "lang": lang, "city": profile.city as Any, "insurance": profile.insurance as Any,
                                      "profile": ["age": profile.age as Any, "pregnant": profile.pregnant]])
    }
    static func bookAppointment(provider: String, doctorId: String, name: String, phone: String, slot: String) async -> [String: Any]? {
        try? await post("/v1/integrations/appointments", ["provider": provider, "doctorId": doctorId, "patientName": name, "phone": phone, "slot": slot])
    }
    static func orderLab(tests: [String], address: String, name: String, phone: String) async -> [String: Any]? {
        try? await post("/v1/integrations/lab-orders", ["tests": tests, "address": address, "patientName": name, "phone": phone])
    }
    static func scheduleReminder(channel: String, to: String, template: String, sendAtISO: String) async -> [String: Any]? {
        try? await post("/v1/integrations/reminders", ["channel": channel, "to": to, "template": template, "sendAt": sendAtISO])
    }
}
