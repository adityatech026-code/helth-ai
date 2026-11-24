'use strict';
const { kb } = require('./nlu');

const DISCLAIMER = {
  en: 'This is general guidance, not a diagnosis. If you feel worse, see a doctor.',
  hi: 'यह सामान्य सलाह है, निदान नहीं। तबीयत बिगड़े तो डॉक्टर को दिखाएं।',
  bn: 'এটি সাধারণ পরামর্শ, রোগনির্ণয় নয়। অবস্থা খারাপ হলে ডাক্তার দেখান।',
  ta: 'இது பொது ஆலோசனை மட்டுமே, நோய் கண்டறிதல் அல்ல. நிலை மோசமானால் மருத்துவரைப் பாருங்கள்.',
  mr: 'हा सामान्य सल्ला आहे, निदान नाही. बरे वाटत नसेल तर डॉक्टरांना भेटा.',
  te: 'ఇది సాధారణ సలహా మాత్రమే, నిర్ధారణ కాదు. పరిస్థితి దిగజారితే వైద్యుడిని కలవండి.',
  gu: 'આ સામાન્ય સલાહ છે, નિદાન નથી. તબિયત બગડે તો ડૉક્ટરને બતાવો.'
};
const HEADLINE = {
  emergency: {
    en: 'This may be an emergency. Call 112 or 108 now, or go to the nearest hospital.',
    hi: 'यह आपातकाल हो सकता है। अभी 112 या 108 पर कॉल करें या नज़दीकी अस्पताल जाएं।',
    bn: 'এটি জরুরি অবস্থা হতে পারে। এখনই ১১২ বা ১০৮ নম্বরে ফোন করুন বা কাছের হাসপাতালে যান।',
    ta: 'இது அவசர நிலையாக இருக்கலாம். உடனே 112 அல்லது 108-ஐ அழையுங்கள் அல்லது அருகிலுள்ள மருத்துவமனைக்குச் செல்லுங்கள்.',
    mr: 'ही आणीबाणी असू शकते. लगेच 112 किंवा 108 वर कॉल करा किंवा जवळच्या रुग्णालयात जा.',
    te: 'ఇది అత్యవసర పరిస్థితి కావచ్చు. వెంటనే 112 లేదా 108కి కాల్ చేయండి లేదా దగ్గరలోని ఆసుపత్రికి వెళ్లండి.',
    gu: 'આ કટોકટી હોઈ શકે. હમણાં 112 અથવા 108 પર કૉલ કરો અથવા નજીકની હોસ્પિટલમાં જાઓ.'
  },
  see_doctor: {
    en: 'I recommend getting some tests and seeing a doctor soon.',
    hi: 'मैं कुछ जांच करवाने और जल्द डॉक्टर से मिलने की सलाह देता हूँ।',
    bn: 'আমি কিছু পরীক্ষা করাতে এবং শীঘ্রই ডাক্তার দেখাতে বলছি।',
    ta: 'சில பரிசோதனைகள் செய்து விரைவில் மருத்துவரைப் பார்ப்பது நல்லது.',
    mr: 'काही तपासण्या करून लवकरच डॉक्टरांना भेटण्याचा सल्ला देतो.',
    te: 'కొన్ని పరీక్షలు చేయించుకుని త్వరలో వైద్యుడిని కలవమని సూచిస్తున్నాను.',
    gu: 'હું કેટલીક તપાસ કરાવવાની અને જલ્દી ડૉક્ટરને મળવાની સલાહ આપું છું.'
  },
  self_care: {
    en: 'This looks manageable at home. Here is what you can do.',
    hi: 'यह घर पर संभाला जा सकता है। आप यह कर सकते हैं।',
    bn: 'এটি বাড়িতেই সামলানো যেতে পারে। আপনি এটি করতে পারেন।',
    ta: 'இதை வீட்டிலேயே கவனித்துக் கொள்ளலாம். நீங்கள் இதைச் செய்யலாம்.',
    mr: 'हे घरी सांभाळता येईल. तुम्ही हे करू शकता.',
    te: 'దీన్ని ఇంట్లోనే చూసుకోవచ్చు. మీరు ఇలా చేయవచ్చు.',
    gu: 'આ ઘરે સંભાળી શકાય. તમે આ કરી શકો છો.'
  }
};
// NOTE: non-English strings are draft translations. Have native-speaking
// clinicians/translators review them before release.

// Days after which a "mild" symptom is no longer treated as self-manageable.
const PERSISTENCE_LIMIT_DAYS = { fever: 3, headache: 3, muscle_pain: 7, indigestion: 7 };
const SEVERITY_RANK = { mild: 1, moderate: 2, severe: 3 };

function mapConditions(symptomIds) {
  const scores = new Map();
  for (const id of symptomIds) {
    const def = kb.symptoms[id];
    for (const c of def.conditions) {
      const key = c.icd10 + '|' + c.name;
      const prev = scores.get(key) || { name: c.name, icd10: c.icd10, score: 0, from: [] };
      prev.score += c.weight;
      prev.from.push(id);
      scores.set(key, prev);
    }
  }
  const list = [...scores.values()].sort((a, b) => b.score - a.score);
  const total = list.reduce((s, c) => s + c.score, 0) || 1;
  return list.slice(0, 5).map((c) => ({
    name: c.name,
    icd10: c.icd10,
    likelihood: +(c.score / total).toFixed(2),
    basedOn: c.from
  }));
}

function triage(extracted, profile = {}) {
  const lang = HEADLINE.self_care[extracted.lang] ? extracted.lang : 'en';
  const reasons = [];
  const ids = extracted.symptoms.map((s) => s.id);

  if (!ids.length && !extracted.emergencyFlags.length) {
    return {
      level: 'unknown',
      headline: 'I did not catch a symptom. Please tell me where it hurts or how you feel.',
      reasons: ['no_symptom_detected'],
      conditions: [],
      disclaimer: DISCLAIMER[lang]
    };
  }

  let level = 'self_care';
  if (extracted.emergencyFlags.length) {
    level = 'emergency';
    reasons.push('red_flag:' + extracted.emergencyFlags.join(','));
  }

  for (const s of extracted.symptoms) {
    const def = kb.symptoms[s.id];
    if (def.tier === 'serious') {
      level = level === 'emergency' ? level : def.emergency && s.severity === 'severe' ? 'emergency' : 'see_doctor';
      reasons.push('serious_symptom:' + s.id);
    } else if (def.tier === 'ambiguous') {
      if (level === 'self_care') level = 'see_doctor';
      reasons.push('ambiguous_symptom:' + s.id);
    }
    if (s.severity === 'severe' && level === 'self_care') {
      level = 'see_doctor';
      reasons.push('severe:' + s.id);
    }
    const limit = PERSISTENCE_LIMIT_DAYS[s.id];
    if (limit && s.duration && s.duration.days >= limit && level === 'self_care') {
      level = 'see_doctor';
      reasons.push('persistent:' + s.id);
    }
  }
  if (ids.length >= 3 && level === 'self_care') {
    level = 'see_doctor';
    reasons.push('multiple_symptoms');
  }

  // High-risk groups never receive self-dosing advice.
  const age = profile.age;
  const highRisk = profile.pregnant || (age != null && (age < 12 || age >= 65));
  if (highRisk && level === 'self_care') {
    level = 'see_doctor';
    reasons.push('high_risk_group');
  }

  const conditions = mapConditions(ids);
  const result = {
    level,
    headline: HEADLINE[level === 'emergency' ? 'emergency' : level === 'see_doctor' ? 'see_doctor' : 'self_care'][lang],
    reasons,
    conditions,
    disclaimer: DISCLAIMER[lang]
  };

  if (level === 'self_care') {
    result.selfCare = extracted.symptoms.map((s) => {
      const d = kb.symptoms[s.id];
      return { symptom: s.id, otc: d.otc, hydration: d.advice.hydration, rest: d.advice.rest, dietTip: d.advice.diet };
    });
    result.medicationNote = extracted.medications.length
      ? 'You mentioned ' + extracted.medications.join(', ') + '. Do not combine it with another dose of the same medicine.'
      : null;
    result.seeDoctorIf = ['symptoms last longer than 3 days', 'symptoms get worse', 'you develop breathing trouble, chest pain, confusion or a rash'];
  } else {
    const tests = new Set();
    const specialties = new Set();
    for (const id of ids) {
      kb.symptoms[id].tests.forEach((t) => tests.add(t));
      specialties.add(kb.symptoms[id].specialty);
    }
    result.recommendedTests = [...tests].slice(0, 6);
    result.specialties = [...specialties];
    result.urgency = level === 'emergency' ? 'now' : reasons.some((r) => r.startsWith('serious')) ? 'within_24h' : 'within_3_days';
  }
  return result;
}

module.exports = { triage, mapConditions, HEADLINE, DISCLAIMER };
