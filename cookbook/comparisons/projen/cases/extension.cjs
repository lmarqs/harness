'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { SharedBlock } = require('../extensions/shared-block.cjs');
const { measure, project, write, read } = require('../lib/measure.cjs');

function shared(root, lines) {
  const p = project(root);
  p.tryRemoveFile('.gitignore');
  new SharedBlock(p, '.gitignore', 'git/ignore', lines);
  return p;
}
function extensionCases() {
  return [
    measure('extension-shared-block', 'bounded-extension', 'Retain manual bytes around a declared block', root => {
      write(root, '.gitignore', '# personal\r\n.idea/\r\n');
      shared(root, ['node_modules/', 'npm-debug.log*']).synth();
      write(root, '.gitignore', read(root, '.gitignore') + 'local-only/\r\n');
      shared(root, ['node_modules/', 'yarn-debug.log*']).synth();
      const after = read(root, '.gitignore');
      return { satisfies: after.startsWith('# personal\r\n.idea/\r\n') && after.endsWith('local-only/\r\n') && !after.includes('npm-debug.log*'), observed: 'A small custom FileBase renderer preserves the manual prefix/suffix during updates.', after };
    }),
    measure('extension-unmount', 'bounded-extension', 'Remove only the managed block when the component is absent', root => {
      write(root, '.gitignore', '# personal\n');
      shared(root, ['node_modules/']).synth();
      const p = project(root);
      p.tryRemoveFile('.gitignore');
      p.synth();
      const exists = fs.existsSync(path.join(root, '.gitignore'));
      const after = exists ? read(root, '.gitignore') : null;
      return { satisfies: after === '# personal\n', observed: 'The editable custom file is not cleaned up, so its block remains after the component is removed.', after };
    }),
    measure('extension-managed-drift', 'bounded-extension', 'Refuse manual changes inside the managed block', root => {
      shared(root, ['node_modules/']).synth();
      write(root, '.gitignore', read(root, '.gitignore').replace('node_modules/', 'manual-inside/'));
      let refused = false;
      try { shared(root, ['node_modules/']).synth(); } catch { refused = true; }
      return { satisfies: refused, observed: 'The small renderer has no ownership baseline and overwrites an edited block.', after: read(root, '.gitignore') };
    }),
  ];
}
module.exports = { extensionCases };
