'use strict';
const express = require('express');
const { extract, kb } = require('./nlu');
const { triage, mapConditions } = require('./triage');
const { findLabs, findDoctors } = require('./care');
const { appointmentProviders, onemg, messaging } = require('./providers');

const app = express();
app.use(express.json({ limit: '256kb' }));

// Privacy: never log request bodies. They contain health data.
app.use((req, _res, next) => { console.log(new Date().toISOString(), req.method, req.path); next(); });

// Minimal shared-secret auth for the stub. Replace with per-user JWT/OAuth.
app.use('/v1', (req, res, next) => {
  const key = process.env.API_KEY;
  if (key && req.get('x-api-key') !== key) return res.status(401).json({ error: 'unauthorized' });
  next();
});

const wrap = (fn) => (req, res) => Promise.resolve(fn(req, res)).catch((e) => res.status(400).json({ error: e.message }));
const need = (v, name) => { if (v === undefined || v === null || v === '') throw new Error('missing field: ' + name); return v; };

app.get('/health', (_req, res) => res.json({ ok: true, kbVersion: kb._meta.version }));

// 1. transcript -> structured symptom data
app.post('/v1/symptoms/extract', wrap((req, res) => {
  const { transcript, lang } = req.body;
  res.json(extract(need(transcript, 'transcript'), lang));
}));

// 2. symptom ids -> probable conditions (ICD-10)
app.post('/v1/symptoms/map', wrap((req, res) => {
  const ids = need(req.body.symptoms, 'symptoms').filter((s) => kb.symptoms[s]);
  res.json({ conditions: mapConditions(ids) });
}));

// 3. full pipeline: transcript -> extraction -> triage -> (care options when not self-care)
app.post('/v1/triage', wrap((req, res) => {
  const { transcript, lang, profile, city, insurance } = req.body;
  const extracted = extract(need(transcript, 'transcript'), lang);
  const result = triage(extracted, profile || {});
  const out = { extracted, triage: result };
  if (result.level === 'see_doctor' || result.level === 'emergency') {
    out.care = {
      labs: findLabs({ city, tests: result.recommendedTests, insurance }),
      doctors: findDoctors({ city, specialties: result.specialties, insurance })
    };
  }
  res.json(out);
}));

app.get('/v1/care/labs', wrap((req, res) => {
  const { city, insurance, tests } = req.query;
  res.json({ labs: findLabs({ city, insurance, tests: tests ? String(tests).split(',') : [] }) });
}));

app.get('/v1/care/doctors', wrap((req, res) => {
  const { city, insurance, specialty } = req.query;
  res.json({ doctors: findDoctors({ city, insurance, specialties: specialty ? [String(specialty)] : [] }) });
}));

// 8. Integration hooks
app.post('/v1/integrations/appointments', wrap(async (req, res) => {
  const { provider, doctorId, patientName, phone, slot } = req.body;
  const p = appointmentProviders[need(provider, 'provider')];
  if (!p) throw new Error('unknown provider; use one of ' + Object.keys(appointmentProviders).join(', '));
  res.json(await p.bookAppointment({ doctorId: need(doctorId, 'doctorId'), patientName: need(patientName, 'patientName'), phone: need(phone, 'phone'), slot: need(slot, 'slot') }));
}));

app.post('/v1/integrations/lab-orders', wrap(async (req, res) => {
  const b = req.body;
  res.json(await onemg.orderLabTest({ tests: need(b.tests, 'tests'), address: need(b.address, 'address'), patientName: need(b.patientName, 'patientName'), phone: need(b.phone, 'phone'), preferredSlot: b.preferredSlot }));
}));

app.post('/v1/integrations/reminders', wrap(async (req, res) => {
  const b = req.body;
  res.json(await messaging.scheduleReminder({ channel: need(b.channel, 'channel'), to: need(b.to, 'to'), template: need(b.template, 'template'), params: b.params || {}, sendAt: need(b.sendAt, 'sendAt') }));
}));

// Habit coach content (also bundled offline in the apps)
app.get('/v1/habits/:id', wrap((req, res) => {
  const h = kb.habits[req.params.id];
  if (!h) throw new Error('unknown habit');
  res.json({ habit: req.params.id, tips: h.tips, mealPlan: kb.mealPlans.default });
}));

if (require.main === module) {
  const port = process.env.PORT || 3000;
  app.listen(port, () => console.log('Swasth Saathi backend listening on :' + port));
}
module.exports = app;
