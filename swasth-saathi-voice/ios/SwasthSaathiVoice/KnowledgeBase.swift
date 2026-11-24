import Foundation

/// Loads the shared knowledge JSON from the app bundle. Works fully offline.
enum KB {
    static let langs = ["en", "hi", "bn", "ta", "mr", "te", "gu"]
    static let root: [String: Any] = load("symptoms")
    static let i18n: [String: Any] = load("i18n")
    static var symptoms: [String: [String: Any]] { root["symptoms"] as? [String: [String: Any]] ?? [:] }

    private static func load(_ name: String) -> [String: Any] {
        guard let url = Bundle.main.url(forResource: name, withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            assertionFailure("Missing \(name).json in bundle"); return [:]
        }
        return obj
    }

    /// All aliases across every language from {"en":[...], "hi":[...]}
    static func aliases(_ map: Any?) -> [String] {
        guard let m = map as? [String: [String]] else { return [] }
        return langs.flatMap { m[$0] ?? [] }
    }
}
