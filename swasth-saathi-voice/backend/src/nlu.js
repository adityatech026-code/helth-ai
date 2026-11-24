'use strict';
// Rule-based multilingual symptom extraction. Mirrors the on-device Kotlin/Swift
// extractors so triage gives the same answer offline and online.
const path = require('path');
const kb = require(path.join(__dirname, '..', '..', 'shared', 'knowledge', 'symptoms.json'));

const LANGS = ['en', 'hi', 'bn', 'ta', 'mr', 'te', 'gu'];

// Convert Devanagari, Bengali, Tamil, Telugu, Gujarati digits to ASCII.
const DIGIT_BASES = [0x0966, 0x09e6, 0x0be6, 0x0c66, 0x0ae6];
function normalizeDigits(s) {
  let out = '';
  for (const ch of s) {
    const cp = ch.codePointAt(0);
    const base = DIGIT_BASES.find((b) => cp >= b && cp <= b + 9);
    out += base !== undefined ? String(cp - base) : ch;
  }
  return out;
}

const NUMBER_WORDS = {
  one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, ten: 10,
  'एक': 1, 'दो': 2, 'तीन': 3, 'चार': 4, 'पांच': 5, 'पाँच': 5, 'छह': 6, 'सात': 7, 'दस': 10,
  'এক': 1, 'দুই': 2, 'তিন': 3, 'চার': 4, 'পাঁচ': 5,
  'ஒரு': 1, 'இரண்டு': 2, 'மூன்று': 3, 'நான்கு': 4, 'ஐந்து': 5,
  'दोन': 2, 'तीन ': 3,
  'ఒక': 1, 'రెండు': 2, 'మూడు': 3, 'నాలుగు': 4, 'ఐదు': 5,
  'એક': 1, 'બે': 2, 'ત્રણ': 3, 'ચાર ': 4, 'પાંચ': 5
};
const UNIT_DAYS = { hour: 1 / 24, day: 1, week: 7, month: 30 };

function norm(text) {
  return normalizeDigits((text || '').toLowerCase().normalize('NFC'));
}

function allAliases(map) {
  return LANGS.flatMap((l) => map[l] || []);
}

function findAll(text, aliases) {
  const hits = [];
  for (const a of aliases) {
    const idx = text.indexOf(a.toLowerCase());
    if (idx >= 0) hits.push({ alias: a, index: idx });
  }
  return hits;
}

function extractDuration(text) {
  const unitEntries = Object.entries(kb.durationUnits);
  const numRe = '(\\d+(?:\\.\\d+)?|' + Object.keys(NUMBER_WORDS).map((w) => w.trim()).join('|') + ')';
  for (const [unit, words] of unitEntries) {
    for (const w of words) {
      const re = new RegExp(numRe + '\\s*' + w.toLowerCase().replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'u');
      const m = text.match(re);
      if (m) {
        const raw = m[1];
        const n = /^\d/.test(raw) ? parseFloat(raw) : NUMBER_WORDS[raw] || NUMBER_WORDS[raw + ' '] || 1;
        return { value: n, unit, days: +(n * UNIT_DAYS[unit]).toFixed(2), raw: m[0] };
      }
    }
  }
  if (/since yesterday|कल से|কাল থেকে|நேற்று முதல்|कालपासून|నిన్నటి నుండి|ગઈકાલથી/u.test(text)) {
    return { value: 1, unit: 'day', days: 1, raw: 'since yesterday' };
  }
  return null;
}

function extractSeverity(text, symptomIndex) {
  // Look in a window around the symptom mention; nearest word wins.
  const start = Math.max(0, symptomIndex - 40);
  const end = Math.min(text.length, symptomIndex + 60);
  const window = text.slice(start, end);
  for (const level of ['severe', 'moderate', 'mild']) {
    if (findAll(window, allAliases(kb.severityWords[level])).length) return level;
  }
  return null;
}

function extract(transcript, lang) {
  const text = norm(transcript);
  const symptoms = [];
  for (const [id, def] of Object.entries(kb.symptoms)) {
    const hits = findAll(text, allAliases(def.aliases));
    if (!hits.length) continue;
    const first = hits.sort((a, b) => a.index - b.index)[0];
    symptoms.push({
      id,
      matchedText: first.alias,
      icd10: def.icd10,
      severity: extractSeverity(text, first.index),
      duration: extractDuration(text)
    });
  }
  const medications = kb.medications.filter((m) => text.includes(m.toLowerCase()));
  const habits = Object.entries(kb.habits)
    .filter(([, def]) => findAll(text, allAliases(def.aliases)).length)
    .map(([id]) => id);
  const emergencyFlags = findAll(text, allAliases(kb.redFlags.emergency)).map((h) => h.alias);
  return { lang: lang || null, symptoms, medications, habits, emergencyFlags };
}

module.exports = { extract, normalizeDigits, kb };
