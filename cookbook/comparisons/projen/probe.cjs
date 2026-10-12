'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { nativeCases } = require('./cases/native.cjs');
const { compositionCases } = require('./cases/composition.cjs');
const { extensionCases } = require('./cases/extension.cjs');
const { packagingCases } = require('./cases/packaging.cjs');
const { ejectCases } = require('./cases/eject.cjs');

function runProbe() {
  const priorPost = process.env.PROJEN_DISABLE_POST;
  const priorDebug = process.env.DEBUG;
  process.env.PROJEN_DISABLE_POST = 'true';
  process.env.DEBUG = 'false';
  try {
    const cases = [...nativeCases(), ...compositionCases(), ...extensionCases(), ...packagingCases(), ...ejectCases()];
    const errors = cases.filter(item => !item.executed).map(item => ({ name: item.name, error: item.error }));
    const codeFiles = [];
    for (const directory of ['ingredients', 'recipes', 'extensions']) {
      function visit(root) {
        for (const item of fs.readdirSync(root, { withFileTypes: true })) {
          const file = path.join(root, item.name);
          if (item.isDirectory()) visit(file);
          else if (file.endsWith('.cjs')) codeFiles.push({ path: path.relative(__dirname, file), lines: fs.readFileSync(file, 'utf8').trimEnd().split('\n').length });
        }
      }
      visit(path.join(__dirname, directory));
    }
    return {
      projen_version: require('projen/package.json').version,
      node_version: process.version,
      execution: 'Real projen API and CLI in disposable /tmp repositories. npm package publication/consumption uses an offline local cache. Cloud resources and agent runners are absent.',
      post_synthesis: 'Disabled: no package installation, lock regeneration or agent execution is hidden inside synthesis.',
      cases,
      execution_errors: errors,
      summary: { executed: cases.length - errors.length, satisfied: cases.filter(item => item.executed && item.satisfies).length, unmet: cases.filter(item => item.executed && !item.satisfies).length },
      custom_code: codeFiles,
      full_replacement_proven: false,
      remaining_work: ['Lossless JSON/TOML/YAML region updates', 'Original-value adoption and restoration', 'Ownership state and removal sweep', 'Saved previews and drift rejection', 'Cross-file transaction and crash recovery', 'Package-manager installation and lockfile regeneration'],
      recommendation: 'Projen is viable for composable, independently versioned components and owned-file synthesis. Cook shared-file lifecycle still requires a custom reconciler. The bounded extension does not establish a full replacement.',
      sources: [
        'https://projen.io/docs/concepts/components/',
        'https://projen.io/docs/concepts/ejecting/',
        'https://projen.io/docs/custom/custom-components/',
        'https://github.com/projen/projen/blob/v0.104.5/src/project.ts',
        'https://github.com/projen/projen/blob/v0.104.5/src/file.ts',
        'https://github.com/projen/projen/blob/v0.104.5/src/javascript/node-package.ts',
      ],
    };
  } finally {
    if (priorPost === undefined) delete process.env.PROJEN_DISABLE_POST; else process.env.PROJEN_DISABLE_POST = priorPost;
    if (priorDebug === undefined) delete process.env.DEBUG; else process.env.DEBUG = priorDebug;
  }
}
if (require.main === module) {
  const report = runProbe();
  fs.writeFileSync(path.join(__dirname, 'results.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report.summary));
  if (report.execution_errors.length) {
    console.error(JSON.stringify(report.execution_errors, null, 2));
    process.exitCode = 1;
  }
}
module.exports = { runProbe };
