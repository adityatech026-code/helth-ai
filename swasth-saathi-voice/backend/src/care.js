'use strict';
const data = require('../data/facilities.json');

const ci = (a, b) => String(a || '').toLowerCase() === String(b || '').toLowerCase();

function findLabs({ city, tests = [], insurance } = {}) {
  return data.labs
    .filter((l) => !city || ci(l.city, city))
    .map((l) => {
      const offered = tests.filter((t) => l.tests.includes(t));
      return { ...l, matchedTests: offered, coverage: tests.length ? offered.length / tests.length : 1,
        insuranceAccepted: insurance ? l.insurance.some((i) => ci(i, insurance)) : null };
    })
    .filter((l) => !tests.length || l.matchedTests.length > 0)
    .sort((a, b) => b.coverage - a.coverage || b.rating - a.rating)
    .slice(0, 5);
}

function findDoctors({ city, specialties = [], insurance } = {}) {
  const wanted = specialties.flatMap((s) => s.split('/').map((x) => x.trim().toLowerCase()));
  return data.doctors
    .filter((d) => !city || ci(d.city, city))
    .filter((d) => !wanted.length || wanted.some((w) => d.specialty.toLowerCase().includes(w) || w.includes(d.specialty.toLowerCase())))
    .map((d) => ({ ...d, insuranceAccepted: insurance ? d.insurance.some((i) => ci(i, insurance)) : null }))
    .sort((a, b) => (b.insuranceAccepted === true) - (a.insuranceAccepted === true) || b.rating - a.rating)
    .slice(0, 5);
}

module.exports = { findLabs, findDoctors };
