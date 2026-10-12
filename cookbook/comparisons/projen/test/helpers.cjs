'use strict';
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

function temporary(test) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'cook-projen-'));
  test.after(() => fs.rmSync(root, { recursive: true, force: true }));
  return root;
}
function write(root, file, content) {
  const target = path.join(root, file);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  if (fs.existsSync(target)) fs.chmodSync(target, 0o644);
  fs.writeFileSync(target, content);
}
function read(root, file) { return fs.readFileSync(path.join(root, file), 'utf8'); }
function inventory(root) {
  const files = {};
  function visit(directory) {
    for (const item of fs.readdirSync(directory, { withFileTypes: true })) {
      const absolute = path.join(directory, item.name);
      if (item.isDirectory()) visit(absolute);
      else files[path.relative(root, absolute)] = {
        content: fs.readFileSync(absolute).toString('base64'),
        mode: fs.statSync(absolute).mode & 0o777,
      };
    }
  }
  visit(root);
  return files;
}
module.exports = { temporary, write, read, inventory };
