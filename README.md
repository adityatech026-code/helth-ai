[README.md](https://github.com/user-attachments/files/35373838/README.md)
# Swasth Saathi Voice

Voice-first health companion for Hindi, English, Bengali, Tamil, Marathi, Telugu and Gujarati.
This is a **starter project**: the structure, offline triage engine, encrypted notebook, STT wiring and API hooks are real; the medical content and partner integrations are placeholders that need clinical and commercial sign-off before any public release.

```
shared/knowledge/   ONE knowledge base used by backend + Android + iOS
  symptoms.json       symptoms, aliases in 7 languages, ICD-10 mappings, OTC advice, tests, habits, meal plan
  i18n.json           translated headlines + disclaimer (draft translations)
backend/            Node/Express: /v1/symptoms/extract, /v1/symptoms/map, /v1/triage, care search, integration hooks
android/            Kotlin + Jetpack Compose, VOSK (offline) + Google SpeechRecognizer (online)
ios/                Swift + SwiftUI, Apple Speech, CryptoKit; generate the Xcode project with XcodeGen
```

## Run the backend

```bash
cd backend
npm install
npm test            # 14 dependency-free tests of extraction + triage + care search
npm start           # http://localhost:3000
curl -s localhost:3000/v1/triage -H 'content-type: application/json' \
  -d '{"transcript":"मुझे हल्का सिरदर्द है २ दिन से","lang":"hi"}'
```

Set `API_KEY` to require an `x-api-key` header. Request bodies are never logged (health data).

| Endpoint | Purpose |
|---|---|
| `POST /v1/symptoms/extract` | transcript → symptom, severity, duration, medication, habits, red flags |
| `POST /v1/symptoms/map` | symptom ids → ranked conditions with ICD-10 codes |
| `POST /v1/triage` | full pipeline; adds nearby labs + doctors (filtered by city, tests, insurance) when not self-care |
| `GET /v1/care/labs`, `/v1/care/doctors` | facility search (mock data in `backend/data/facilities.json`) |
| `POST /v1/integrations/appointments` | Practo / 1mg booking hook |
| `POST /v1/integrations/lab-orders` | lab test ordering hook |
| `POST /v1/integrations/reminders` | SMS / WhatsApp reminder hook |

Partner hooks live in `backend/src/providers/index.js`. Each stub has a `TODO` where the real vendor client goes; keep API keys on the server.

## Android

Open `android/` in Android Studio (Koala or newer). The Gradle build copies `shared/knowledge` into the app's assets.
Emulator reaches the backend at `10.0.2.2:3000` (`BACKEND_URL` in `app/build.gradle.kts`).

**Speech-to-text.** `SpeechEngineFactory` picks Google `SpeechRecognizer` when online and VOSK when offline.
VOSK models are not bundled (size). Per alphacephei.com/vosk/models, small mobile models exist for **Hindi** (`vosk-model-small-hi-0.22`), **Gujarati** (`small-gu-0.42`), **Telugu** (`small-te-0.42`) and **Indian English** (`small-en-in-0.4`). **No VOSK model is listed for Bengali, Tamil or Marathi.** For those, the app uses Google's recognizer (it can work offline if the user downloads the language pack in Android settings) and always offers typed input. Unzip each model to `app/src/main/assets/model-<lang>/`.

## iOS

```bash
brew install xcodegen && cd ios && xcodegen && open SwasthSaathiVoice.xcodeproj
```

Uses `SFSpeechRecognizer` per language; on-device recognition is used when offline **if the device supports it for that locale**. Indian-language on-device support varies by iOS version, so test each one. To get fully offline STT for hi/gu/te, implement `SpeechProvider` with the VOSK iOS wrapper.

## How the requirements map to code

| # | Requirement | Where |
|---|---|---|
| 1 | Multilingual STT → structured data | `speech/*`, `SymptomExtractor` (Kotlin/Swift), `backend/src/nlu.js` |
| 2 | Symptom → condition mapping with ICD-10 | `symptoms.json` `conditions[]`, `mapConditions` |
| 3 | Triage: self-care vs tests + labs + doctors | `TriageEngine`, `backend/src/triage.js`, `care.js` |
| 4 | Notebook, trends, PDF | `NotebookStore`, `Trends`, `PdfExporter` |
| 5 | Habit coach, meal plan, reminders | `HabitCoach` (WorkManager / local notifications), `symptoms.json` `habits`, `mealPlans` |
| 6 | Encrypted local storage, optional backup, offline triage | Keystore / Keychain + AES-256-GCM; `BackupCrypto` (passphrase, PBKDF2 + AES-GCM); triage engines run on-device |
| 7 | Short follow-up dialogue | `Dialogue` ("what next", "last blood test", "progress", "repeat", "book") |
| 8 | Integration hooks | `backend/src/providers`, `BackendClient` |

Offline-first: the app always triages on-device. The backend only adds labs/doctors and booking. Android sets `allowBackup=false`; iOS excludes the notebook from device backup. Cloud backup is opt-in and encrypted before it leaves the phone, so a forgotten passphrase means the backup cannot be recovered.

## Before this goes near real patients

1. **Clinical review.** Every dose, red flag, test list and condition weight in `symptoms.json` is a draft. Have licensed doctors review and own it; add contraindication checks (kidney/liver disease, allergies, pregnancy, current medicines, age bands, paediatric dosing).
2. **"Certified" knowledge graph.** The ICD-10 codes here are a small hand-mapped set. Using WHO/ICD content, SNOMED CT or a commercial symptom checker at scale requires the right licences; treat those as replacements for `symptoms.json`, behind the same interfaces.
3. **Regulation and privacy.** Health data is sensitive personal data under India's DPDP Act, 2023; a symptom checker that recommends medicines may also draw medical-device or telemedicine rules. Get legal advice on consent flows, data residency and the OTC-advice feature specifically.
4. **Translations.** `i18n.json` and the follow-up keyword lists are draft translations. Have native speakers review them. Advice text in `symptoms.json` is English only so far; spoken output uses the device TTS voice for the selected language.
5. **Extraction is keyword-based.** It is deterministic, auditable and works offline, but misses negation ("no fever"), unusual phrasing and dialects. Add negation handling and test with real speakers; an on-device or server NLU model can sit behind the same `extract` function.
6. **Auth.** The backend's `x-api-key` check is a placeholder. Use per-user auth before storing or syncing anything.
7. **Not compiled here.** The Node backend is tested; the Android and iOS code was written without an SDK/Xcode available, so expect small compile fixes on first build.
