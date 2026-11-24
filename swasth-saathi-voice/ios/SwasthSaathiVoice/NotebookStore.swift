import Foundation
import CryptoKit
import Security
import CommonCrypto

/// Local-only encrypted notebook. AES-256-GCM via CryptoKit; the key lives in the Keychain
/// (this-device-only, available after first unlock) and never leaves the device.
final class NotebookStore {
    private let url: URL = {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("notebook.enc")
    }()
    private let encoder: JSONEncoder = { let e = JSONEncoder(); e.dateEncodingStrategy = .secondsSince1970; return e }()
    private let decoder: JSONDecoder = { let d = JSONDecoder(); d.dateDecodingStrategy = .secondsSince1970; return d }()

    func all() -> [Entry] {
        guard let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return text.split(separator: "\n").compactMap { line in
            guard let raw = Data(base64Encoded: String(line)), let box = try? AES.GCM.SealedBox(combined: raw),
                  let plain = try? AES.GCM.open(box, using: key()) else { return nil }
            return try? decoder.decode(Entry.self, from: plain)
        }.sorted { $0.timestamp > $1.timestamp }
    }

    func add(_ e: Entry) {
        guard let plain = try? encoder.encode(e), let sealed = try? AES.GCM.seal(plain, using: key()).combined else { return }
        let line = sealed.base64EncodedString() + "\n"
        if let h = try? FileHandle(forWritingTo: url) { h.seekToEndOfFile(); h.write(Data(line.utf8)); try? h.close() }
        else { try? line.write(to: url, atomically: true, encoding: .utf8) }
        try? (url as NSURL).setResourceValue(URLFileProtection.complete, forKey: .fileProtectionKey) // iOS data protection
        var rv = URLResourceValues(); rv.isExcludedFromBackup = true; var u = url; try? u.setResourceValues(rv) // keep out of iCloud device backup
    }

    func wipe() { try? FileManager.default.removeItem(at: url) }

    func exportJSON() -> Data { (try? encoder.encode(all())) ?? Data() }
    func importJSON(_ data: Data) { (try? decoder.decode([Entry].self, from: data))?.forEach(add) }

    // MARK: Keychain-held key
    private func key() -> SymmetricKey {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: "swasth_notebook_key",
                                kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        if SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data { return SymmetricKey(data: d) }
        let k = SymmetricKey(size: .bits256)
        let data = k.withUnsafeBytes { Data($0) }
        SecItemAdd([kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: "swasth_notebook_key", kSecValueData as String: data,
                    kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly] as CFDictionary, nil)
        return k
    }
}

/// Optional cloud backup: encrypted on-device with a passphrase-derived key, so the cloud sees only ciphertext.
/// Format: salt[16] || combined(AES-GCM nonce+ciphertext+tag). Cross-platform note: the Android format is
/// salt[16] || iv[12] || ciphertext+tag, which is the same bytes layout as CryptoKit's `combined`.
enum BackupCrypto {
    static func seal(_ plain: Data, passphrase: String) throws -> Data {
        var salt = Data(count: 16); _ = salt.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }
        let box = try AES.GCM.seal(plain, using: derive(passphrase, salt))
        return salt + box.combined!
    }
    static func open(_ blob: Data, passphrase: String) throws -> Data {
        let salt = blob.prefix(16)
        return try AES.GCM.open(AES.GCM.SealedBox(combined: blob.dropFirst(16)), using: derive(passphrase, Data(salt)))
    }
    private static func derive(_ pass: String, _ salt: Data) -> SymmetricKey {
        var out = [UInt8](repeating: 0, count: 32)
        _ = salt.withUnsafeBytes { s in
            CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), pass, pass.utf8.count, s.bindMemory(to: UInt8.self).baseAddress, salt.count,
                                 CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 210_000, &out, 32)
        }
        return SymmetricKey(data: out)
    }
}
