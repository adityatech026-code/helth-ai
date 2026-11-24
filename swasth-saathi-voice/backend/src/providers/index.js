'use strict';
// Integration hooks. Each provider exposes the same small interface so the real
// vendor SDK/REST client can replace the stub without touching the routes.
// All stubs return deterministic fake data and log what a real call would send.
// Real credentials belong in environment variables, never in the mobile apps.

const crypto = require('crypto');
const id = (p) => p + '-' + crypto.randomBytes(4).toString('hex');

/** @typedef {{doctorId:string, patientName:string, phone:string, slot:string}} AppointmentRequest */

const practo = {
  name: 'practo',
  // TODO: replace with Practo partner API. Needs partner agreement + API key (PRACTO_API_KEY).
  async bookAppointment(/** @type {AppointmentRequest} */ req) {
    return { provider: 'practo', status: 'pending_confirmation', bookingId: id('PRC'), echo: req, stub: true };
  },
  async availableSlots(doctorId) {
    return ['2026-11-26T10:00:00+05:30', '2026-11-26T16:30:00+05:30', '2026-11-27T11:15:00+05:30'].map((s) => ({ doctorId, slot: s }));
  }
};

const onemg = {
  name: '1mg',
  // TODO: replace with 1mg Labs / consult partner API (ONEMG_API_KEY).
  async bookAppointment(req) {
    return { provider: '1mg', status: 'pending_confirmation', bookingId: id('1MG'), echo: req, stub: true };
  },
  async orderLabTest({ tests, address, patientName, phone, preferredSlot }) {
    return { provider: '1mg', status: 'scheduled', orderId: id('LAB'), tests, address, patientName, phone, preferredSlot, homeCollection: true, stub: true };
  }
};

const messaging = {
  // TODO: Twilio / Gupshup / Meta WhatsApp Cloud API. WhatsApp requires pre-approved templates.
  async send({ channel, to, template, params }) {
    if (!['sms', 'whatsapp'].includes(channel)) throw new Error('channel must be sms or whatsapp');
    return { channel, to, template, params, messageId: id('MSG'), status: 'queued', stub: true };
  },
  async scheduleReminder({ channel, to, template, params, sendAt }) {
    return { reminderId: id('REM'), channel, to, template, params, sendAt, status: 'scheduled', stub: true };
  }
};

const appointmentProviders = { practo, '1mg': onemg };
module.exports = { appointmentProviders, onemg, messaging };
