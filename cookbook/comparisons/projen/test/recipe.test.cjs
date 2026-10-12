'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { temporary, write, read, inventory } = require('./helpers.cjs');

process.env.PROJEN_DISABLE_POST = 'true';
function recipe(options) {
  let makeRecipe;
  try { ({ makeRecipe } = require('../recipes/development/recipe.cjs')); }
  catch (error) { assert.fail(`Composition recipe is missing: ${error.code}`); }
  return makeRecipe(options);
}

test('explicit producer exports compose into one Git writer', t => {
  const root = temporary(t);
  const project = recipe({ outdir: root, manager: 'npm', nodeVersion: '20', git: true, typesNode: true });
  assert.deepEqual(inventory(root), {}, 'construction must leave the repository unchanged');
  project.synth();
  const pkg = JSON.parse(read(root, 'package.json'));
  assert.equal(pkg.packageManager, 'npm@10.9.8');
  assert.equal(pkg.devDependencies['@types/node'], '^20.0.0');
  assert.match(read(root, 'mise.toml'), /node = "20"/);
  const ignore = read(root, '.gitignore');
  assert.equal(ignore.split('\n').filter(line => line === 'node_modules/').length, 1);
  assert.match(ignore, /npm-debug\.log\*/);
  assert.doesNotMatch(ignore, /package-lock\.json|yarn\.lock/);
});

test('a fresh Node version regenerates the related native configuration together', t => {
  const root = temporary(t);
  recipe({ outdir: root, nodeVersion: '20', typesNode: true }).synth();
  recipe({ outdir: root, nodeVersion: '22', typesNode: true }).synth();
  const pkg = JSON.parse(read(root, 'package.json'));
  assert.equal(pkg.engines.node, '>= 22.0.0');
  assert.equal(pkg.devDependencies['@types/node'], '^22.0.0');
  assert.match(read(root, 'mise.toml'), /node = "22"/);
  assert.doesNotMatch(read(root, 'mise.toml'), /node = "20"/);
});

test('manager changes update install tasks and keep both durable lockfiles', t => {
  const root = temporary(t);
  write(root, 'package-lock.json', '{"lockfileVersion":3,"packages":{}}\n');
  write(root, 'yarn.lock', '# durable Yarn lock\n');
  const before = { npm: read(root, 'package-lock.json'), yarn: read(root, 'yarn.lock') };
  recipe({ outdir: root, manager: 'npm' }).synth();
  recipe({ outdir: root, manager: 'yarn' }).synth();
  const pkg = JSON.parse(read(root, 'package.json'));
  assert.equal(pkg.packageManager, 'yarn@4.17.0');
  assert.match(read(root, 'mise.toml'), /"npm:yarn" = "4.17.0"/);
  assert.match(read(root, '.projen/tasks.json'), /yarn install --immutable/);
  assert.equal(read(root, 'package-lock.json'), before.npm);
  assert.equal(read(root, 'yarn.lock'), before.yarn);
  recipe({ outdir: root, manager: 'npm' }).synth();
  assert.equal(JSON.parse(read(root, 'package.json')).packageManager, 'npm@10.9.8');
  assert.match(read(root, '.projen/tasks.json'), /npm ci/);
  assert.equal(fs.existsSync(path.join(root, '.yarnrc.yml')), false);
});

test('Git can be explicitly omitted even when Node and mise are present', t => {
  const root = temporary(t);
  recipe({ outdir: root, git: false }).synth();
  assert.equal(fs.existsSync(path.join(root, '.gitignore')), false);
  assert.equal(fs.existsSync(path.join(root, 'package.json')), true);
  assert.equal(fs.existsSync(path.join(root, 'mise.toml')), true);
});

test('regenerating an unchanged recipe is byte and mode idempotent', t => {
  const root = temporary(t);
  recipe({ outdir: root }).synth();
  const before = inventory(root);
  recipe({ outdir: root }).synth();
  assert.deepEqual(inventory(root), before);
});

test('invalid options fail during declaration without changing a repository', t => {
  const root = temporary(t);
  write(root, 'sentinel', 'untouched\n');
  const before = inventory(root);
  assert.throws(() => recipe({ outdir: root, manager: 'unknown' }), /manager/);
  assert.throws(() => recipe({ outdir: root, nodeVersion: 'latest' }), /version/);
  assert.deepEqual(inventory(root), before);
});
