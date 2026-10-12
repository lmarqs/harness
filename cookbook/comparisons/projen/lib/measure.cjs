'use strict';
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { Project, LogLevel } = require('projen');
const { write, read, inventory } = require('../test/helpers.cjs');

function project(root) {
  return new Project({ name: 'probe', outdir: root, logging: { level: LogLevel.OFF } });
}
function measure(name, layer, requirement, body) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'cook-projen-probe-'));
  try {
    return { name, layer, requirement, executed: true, ...body(root) };
  } catch (error) {
    return { name, layer, requirement, executed: false, error: error.stack };
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
}
module.exports = { measure, project, write, read, inventory };
