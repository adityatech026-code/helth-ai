import Foundation

/// On-device port of backend/src/triage.js: core triage works with zero connectivity.
enum TriageEngine {
    private static let persistenceLimitDays: [String: Double] = ["fever": 3, "headache": 3, "muscle_pain": 7, "indigestion": 7]

    private static func i18n(_ section: String, _ key: String?, _ lang: String) -> String {
        var node = KB.i18n[section] as? [String: Any] ?? [:]
        if let key { node = node[key] as? [String: Any] ?? [:] }
        return node[lang] as? String ?? node["en"] as? String ?? ""
    }

    static func triage(_ ex: Extraction, profile: UserProfile = UserProfile()) -> TriageResult {
        let lang = KB.langs.contains(ex.lang) ? ex.lang : "en"
        let disclaimer = i18n("disclaimer", nil, lang)
        if ex.symptoms.isEmpty && ex.emergencyFlags.isEmpty {
            return TriageResult(level: "unknown", headline: "I did not catch a symptom. Please tell me where it hurts or how you feel.",
                                reasons: ["no_symptom_detected"], conditions: [], selfCare: [], tests: [], specialties: [], urgency: nil, disclaimer: disclaimer)
        }
        var reasons: [String] = []; var level = "self_care"
        if !ex.emergencyFlags.isEmpty { level = "emergency"; reasons.append("red_flag:" + ex.emergencyFlags.joined(separator: ",")) }

        for s in ex.symptoms {
            let def = KB.symptoms[s.id] ?? [:]
            switch def["tier"] as? String {
            case "serious":
                if level != "emergency" { level = ((def["emergency"] as? Bool) == true && s.severity == "severe") ? "emergency" : "see_doctor" }
                reasons.append("serious_symptom:\(s.id)")
            case "ambiguous":
                if level == "self_care" { level = "see_doctor" }; reasons.append("ambiguous_symptom:\(s.id)")
            default: break
            }
            if s.severity == "severe" && level == "self_care" { level = "see_doctor"; reasons.append("severe:\(s.id)") }
            if let limit = persistenceLimitDays[s.id], let d = s.durationDays, d >= limit, level == "self_care" {
                level = "see_doctor"; reasons.append("persistent:\(s.id)")
            }
        }
        if ex.symptoms.count >= 3 && level == "self_care" { level = "see_doctor"; reasons.append("multiple_symptoms") }
        let highRisk = profile.pregnant || (profile.age.map { $0 < 12 || $0 >= 65 } ?? false)
        if highRisk && level == "self_care" { level = "see_doctor"; reasons.append("high_risk_group") }

        let ids = ex.symptoms.map(\.id)
        let headline = i18n("headline", level, lang)
        let conditions = mapConditions(ids)

        if level == "self_care" {
            let care = ex.symptoms.map { s -> SelfCareAdvice in
                let d = KB.symptoms[s.id] ?? [:]; let otc = d["otc"] as? [String: String] ?? [:]; let adv = d["advice"] as? [String: String] ?? [:]
                return SelfCareAdvice(symptom: s.id, drug: otc["drug"] ?? "", dose: otc["dose"] ?? "", max: otc["max"] ?? "", avoid: otc["avoid"] ?? "",
                                      hydration: adv["hydration"] ?? "", rest: adv["rest"] ?? "", dietTip: adv["diet"] ?? "")
            }
            return TriageResult(level: level, headline: headline, reasons: reasons, conditions: conditions, selfCare: care, tests: [], specialties: [], urgency: nil, disclaimer: disclaimer)
        }
        var tests: [String] = []; var specs: [String] = []
        for id in ids {
            let d = KB.symptoms[id] ?? [:]
            for t in d["tests"] as? [String] ?? [] where !tests.contains(t) { tests.append(t) }
            if let sp = d["specialty"] as? String, !specs.contains(sp) { specs.append(sp) }
        }
        let urgency = level == "emergency" ? "now" : (reasons.contains { $0.hasPrefix("serious") } ? "within_24h" : "within_3_days")
        return TriageResult(level: level, headline: headline, reasons: reasons, conditions: conditions, selfCare: [], tests: Array(tests.prefix(6)), specialties: specs, urgency: urgency, disclaimer: disclaimer)
    }

    static func mapConditions(_ ids: [String]) -> [Condition] {
        var acc: [String: (name: String, icd: String, score: Double)] = [:]
        for id in ids {
            for c in KB.symptoms[id]?["conditions"] as? [[String: Any]] ?? [] {
                let name = c["name"] as? String ?? "", icd = c["icd10"] as? String ?? "", w = c["weight"] as? Double ?? 0
                let key = icd + "|" + name
                acc[key] = (name, icd, (acc[key]?.score ?? 0) + w)
            }
        }
        let total = max(acc.values.reduce(0) { $0 + $1.score }, 0.0001)
        return acc.values.sorted { $0.score > $1.score }.prefix(5).map { Condition(name: $0.name, icd10: $0.icd, likelihood: (($0.score / total) * 100).rounded() / 100) }
    }
}
