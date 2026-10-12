'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

test('the probe measures native behavior using an initially empty npm cache', t => {
  const cache = fs.mkdtempSync(path.join(os.tmpdir(), 'cook-projen-empty-cache-'));
  const priorCache = process.env.npm_config_cache;
  process.env.npm_config_cache = cache;
  t.after(() => {
    if (priorCache === undefined) delete process.env.npm_config_cache;
    else process.env.npm_config_cache = priorCache;
    fs.rmSync(cache, { recursive: true, force: true });
  });
  let runProbe;
  try { ({ runProbe } = require('../probe.cjs')); }
  catch (error) { assert.fail(`Comparison runner is missing: ${error.code}`); }
  const report = runProbe();
  assert.equal(report.projen_version, '0.104.5');
  assert.equal(report.execution_errors.length, 0, JSON.stringify(report.execution_errors));
  const cases = Object.fromEntries(report.cases.map(item => [item.name, item]));
  for (const name of ['composition', 'node-version-switch', 'manager-switch', 'explicit-git-adapter', 'independent-package', 'native-eject']) {
    assert.equal(cases[name].satisfies, true, name + ': ' + JSON.stringify(cases[name]));
  }
  for (const name of ['native-manual-gitignore', 'native-manual-json', 'native-manual-toml', 'native-adopt-restore', 'native-edited-file-removal', 'native-default-declaration-purity', 'native-eject-preserves-manual-edits', 'native-cross-file-rollback', 'extension-unmount']) {
    assert.equal(cases[name].satisfies, false, name + ': ' + JSON.stringify(cases[name]));
  }
  assert.equal(cases['extension-shared-block'].satisfies, true);
  assert.equal(report.full_replacement_proven, false);
});
