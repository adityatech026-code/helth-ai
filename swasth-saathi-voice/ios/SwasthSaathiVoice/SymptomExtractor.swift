import Foundation

/// On-device port of backend/src/nlu.js. Keep behaviour identical.
enum SymptomExtractor {
    private static let digitBases: [UInt32] = [0x0966, 0x09E6, 0x0BE6, 0x0C66, 0x0AE6]
    private static let numberWords: [String: Int] = [
        "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7, "ten": 10,
        "एक": 1, "दो": 2, "तीन": 3, "चार": 4, "पांच": 5, "पाँच": 5, "छह": 6, "सात": 7, "दस": 10,
        "এক": 1, "দুই": 2, "তিন": 3, "চার": 4, "পাঁচ": 5,
        "ஒரு": 1, "இரண்டு": 2, "மூன்று": 3, "நான்கு": 4, "ஐந்து": 5,
        "दोन": 2, "ఒక": 1, "రెండు": 2, "మూడు": 3, "నాలుగు": 4, "ఐదు": 5,
        "એક": 1, "બે": 2, "ત્રણ": 3, "પાંચ": 5
    ]
    private static let unitDays: [String: Double] = ["hour": 1.0 / 24, "day": 1, "week": 7, "month": 30]

    static func normalize(_ s: String) -> String {
        var out = String.UnicodeScalarView()
        for u in s.lowercased().unicodeScalars {
            if let base = digitBases.first(where: { u.value >= $0 && u.value <= $0 + 9 }),
               let d = Unicode.Scalar(48 + (u.value - base)) { out.append(d) } else { out.append(u) }
        }
        return String(out).precomposedStringWithCanonicalMapping
    }

    static func extract(_ transcript: String, lang: String) -> Extraction {
        let text = normalize(transcript)
        var symptoms: [ExtractedSymptom] = []
        for (id, def) in KB.symptoms {
            let hits = KB.aliases(def["aliases"]).compactMap { a -> (String, Int)? in
                guard let r = text.range(of: a.lowercased()) else { return nil }
                return (a, text.distance(from: text.startIndex, to: r.lowerBound))
            }
            guard let first = hits.min(by: { $0.1 < $1.1 }) else { continue }
            let dur = duration(text)
            symptoms.append(ExtractedSymptom(id: id, matchedText: first.0, icd10: def["icd10"] as? String ?? "",
                                             severity: severity(text, at: first.1), durationDays: dur?.0, durationRaw: dur?.1))
        }
        symptoms.sort { $0.id < $1.id }
        let meds = (KB.root["medications"] as? [String] ?? []).filter { text.contains($0.lowercased()) }
        let habitsDef = KB.root["habits"] as? [String: [String: Any]] ?? [:]
        let habits = habitsDef.filter { KB.aliases($0.value["aliases"]).contains { a in text.contains(a.lowercased()) } }.map(\.key).sorted()
        let red = (KB.root["redFlags"] as? [String: Any])?["emergency"]
        let flags = KB.aliases(red).filter { text.contains($0.lowercased()) }
        return Extraction(lang: lang, symptoms: symptoms, medications: meds, habits: habits, emergencyFlags: flags)
    }

    private static func severity(_ text: String, at idx: Int) -> String? {
        let chars = Array(text)
        let window = String(chars[max(0, idx - 40)..<min(chars.count, idx + 60)])
        let sev = KB.root["severityWords"] as? [String: Any] ?? [:]
        for level in ["severe", "moderate", "mild"] where KB.aliases(sev[level]).contains(where: { window.contains($0.lowercased()) }) { return level }
        return nil
    }

    private static func duration(_ text: String) -> (Double, String)? {
        let units = KB.root["durationUnits"] as? [String: [String]] ?? [:]
        let numAlt = (["\\d+(?:\\.\\d+)?"] + numberWords.keys.map(NSRegularExpression.escapedPattern(for:))).joined(separator: "|")
        for unit in ["hour", "day", "week", "month"] {
            for w in units[unit] ?? [] {
                guard let re = try? NSRegularExpression(pattern: "(\(numAlt))\\s*\(NSRegularExpression.escapedPattern(for: w.lowercased()))"),
                      let m = re.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
                      let r = Range(m.range(at: 1), in: text) else { continue }
                let raw = String(text[r])
                let n = Double(raw) ?? Double(numberWords[raw] ?? 1)
                return (n * (unitDays[unit] ?? 1), String(text[Range(m.range, in: text)!]))
            }
        }
        return nil
    }
}
