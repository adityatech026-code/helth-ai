import Foundation

enum Lang: String, CaseIterable, Identifiable, Codable {
    case en, hi, bn, ta, mr, te, gu
    var id: String { rawValue }
    var locale: Locale { Locale(identifier: "\(rawValue)-IN") }
    var label: String {
        switch self {
        case .en: return "English"; case .hi: return "हिन्दी"; case .bn: return "বাংলা"; case .ta: return "தமிழ்"
        case .mr: return "मराठी"; case .te: return "తెలుగు"; case .gu: return "ગુજરાતી"
        }
    }
}

struct ExtractedSymptom: Codable, Hashable {
    let id: String; let matchedText: String; let icd10: String
    let severity: String?; let durationDays: Double?; let durationRaw: String?
}

struct Extraction {
    let lang: String
    let symptoms: [ExtractedSymptom]
    let medications: [String]
    let habits: [String]
    let emergencyFlags: [String]
}

struct Condition: Hashable { let name: String; let icd10: String; let likelihood: Double }

struct SelfCareAdvice: Hashable {
    let symptom: String, drug: String, dose: String, max: String, avoid: String
    let hydration: String, rest: String, dietTip: String
}

struct TriageResult {
    let level: String            // emergency | see_doctor | self_care | unknown
    let headline: String
    let reasons: [String]
    let conditions: [Condition]
    let selfCare: [SelfCareAdvice]
    let tests: [String]
    let specialties: [String]
    let urgency: String?
    let disclaimer: String
}

struct UserProfile { var age: Int? = nil; var pregnant = false; var city: String? = "Lucknow"; var insurance: String? = nil }

struct Entry: Codable, Identifiable {
    var id = UUID()
    let timestamp: Date
    let kind: String             // symptom | lab | note
    let lang: String
    let transcript: String
    let symptoms: [String]
    let severity: [String: String]
    let medications: [String]
    let level: String
    let recommendation: String
}
