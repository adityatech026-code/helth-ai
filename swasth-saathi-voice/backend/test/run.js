'use strict';
// Dependency-free tests for the core logic (no express needed).
const assert = require('assert');
const { extract, normalizeDigits } = require('../src/nlu');
const { triage } = require('../src/triage');
const { findLabs, findDoctors } = require('../src/care');

let passed = 0;
const t = (name, fn) => { fn(); passed++; console.log('ok  -', name); };

t('digit normalisation (Devanagari, Bengali, Tamil)', () => {
  assert.strictEqual(normalizeDigits('२ ३ ৪ ௫'), '2 3 4 5');
});

t('English: mild headache 2 days -> self_care with paracetamol', () => {
  const e = extract('I have a mild headache since 2 days, took dolo', 'en');
  assert.strictEqual(e.symptoms[0].id, 'headache');
  assert.strictEqual(e.symptoms[0].severity, 'mild');
  assert.strictEqual(e.symptoms[0].duration.days, 2);
  assert.ok(e.medications.includes('dolo'));
  const r = triage(e);
  assert.strictEqual(r.level, 'self_care');
  assert.ok(/Paracetamol/.test(r.selfCare[0].otc.drug));
});

t('Hindi: हल्का सिरदर्द और बुखार, 2 दिन', () => {
  const e = extract('मुझे हल्का सिरदर्द और बुखार है २ दिन से', 'hi');
  assert.deepStrictEqual(e.symptoms.map((s) => s.id).sort(), ['fever', 'headache']);
  assert.strictEqual(triage(e).level, 'self_care');
});

t('Hindi: persistent fever (4 days) escalates', () => {
  const r = triage(extract('बुखार 4 दिन से है', 'hi'));
  assert.strictEqual(r.level, 'see_doctor');
  assert.ok(r.reasons.some((x) => x.startsWith('persistent')));
});

t('Bengali, Tamil, Marathi, Telugu, Gujarati symptom detection', () => {
  const cases = [['আমার জ্বর', 'fever'], ['எனக்கு தலைவலி', 'headache'], ['मला खोकला आहे', 'cough'], ['నాకు కడుపు నొప్పి', 'abdominal_pain'], ['મને અપચો છે', 'indigestion']];
  for (const [text, id] of cases) assert.ok(extract(text).symptoms.some((s) => s.id === id), text);
});

t('severe chest pain -> emergency', () => {
  const r = triage(extract('severe chest pain since 1 hour', 'en'));
  assert.strictEqual(r.level, 'emergency');
  assert.strictEqual(r.urgency, 'now');
  assert.ok(r.recommendedTests.includes('ECG'));
});

t('chest pain without severity -> see_doctor with ECG + cardiologist', () => {
  const r = triage(extract('chest pain', 'en'));
  assert.strictEqual(r.level, 'see_doctor');
  assert.ok(r.specialties.includes('Cardiologist'));
});

t('red flag text triggers emergency even with a mild symptom', () => {
  assert.strictEqual(triage(extract('headache and slurred speech', 'en')).level, 'emergency');
});

t('child / pregnant / elderly never get self-dosing advice', () => {
  const e = extract('mild fever', 'en');
  for (const p of [{ age: 5 }, { age: 70 }, { pregnant: true }]) {
    const r = triage(e, p);
    assert.strictEqual(r.level, 'see_doctor');
    assert.strictEqual(r.selfCare, undefined);
  }
});

t('ambiguous fatigue -> tests incl. CBC/TSH', () => {
  const r = triage(extract('always tired, no energy', 'en'));
  assert.strictEqual(r.level, 'see_doctor');
  assert.ok(r.recommendedTests.some((x) => /CBC/.test(x)));
});

t('habit detection in multiple languages', () => {
  assert.deepStrictEqual(extract('मैं रोज सिगरेट पीता हूँ और मीठा बहुत खाता हूँ').habits.sort(), ['smoking', 'sugar']);
  assert.ok(extract("I can't sleep at night").habits.includes('sleep'));
});

t('response language follows input language', () => {
  assert.ok(/आपातकाल/.test(triage(extract('सीने में दर्द बहुत तेज', 'hi')).headline));
});

t('no symptom -> unknown', () => {
  assert.strictEqual(triage(extract('hello there', 'en')).level, 'unknown');
});

t('care search filters by city, tests and insurance', () => {
  const labs = findLabs({ city: 'lucknow', tests: ['ECG'], insurance: 'Star Health' });
  assert.ok(labs.length >= 1 && labs.every((l) => l.matchedTests.includes('ECG')));
  const docs = findDoctors({ city: 'Lucknow', specialties: ['Cardiologist'], insurance: 'Star Health' });
  assert.strictEqual(docs[0].specialty, 'Cardiologist');
  assert.strictEqual(docs[0].insuranceAccepted, true);
});

console.log('\n' + passed + ' tests passed');
