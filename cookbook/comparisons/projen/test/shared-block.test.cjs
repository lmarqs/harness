'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { Project, LogLevel } = require('projen');
const { temporary, write, read } = require('./helpers.cjs');

function shared(project, lines) {
  let SharedBlock;
  try { ({ SharedBlock } = require('../extensions/shared-block.cjs')); }
  catch (error) { assert.fail(`Shared-block extension is missing: ${error.code}`); }
  project.tryRemoveFile('.gitignore');
  return new SharedBlock(project, '.gitignore', 'git/ignore', lines);
}

test('a small FileBase extension preserves manual bytes while its block remains declared', t => {
  const root = temporary(t);
  write(root, '.gitignore', '# personal\r\n.idea/\r\n');
  const project = new Project({ name: 'shared', outdir: root, logging: { level: LogLevel.OFF } });
  shared(project, ['node_modules/', 'npm-debug.log*']);
  project.synth();
  assert.ok(read(root, '.gitignore').startsWith('# personal\r\n.idea/\r\n'));
  write(root, '.gitignore', read(root, '.gitignore') + 'local-only/\r\n');
  const update = new Project({ name: 'shared', outdir: root, logging: { level: LogLevel.OFF } });
  shared(update, ['node_modules/', 'yarn-debug.log*']);
  update.synth();
  const result = read(root, '.gitignore');
  assert.ok(result.startsWith('# personal\r\n.idea/\r\n'));
  assert.ok(result.endsWith('local-only/\r\n'));
  assert.match(result, /yarn-debug\.log\*/);
  assert.doesNotMatch(result, /npm-debug\.log\*/);
});

test('the small extension rejects duplicate and malformed ownership markers', t => {
  const root = temporary(t);
  write(root, '.gitignore', '# cook:begin git/ignore\nunterminated\n');
  const project = new Project({ name: 'bad', outdir: root, logging: { level: LogLevel.OFF } });
  shared(project, ['node_modules/']);
  assert.throws(() => project.synth(), /Malformed/);
  assert.equal(read(root, '.gitignore'), '# cook:begin git/ignore\nunterminated\n');
});
