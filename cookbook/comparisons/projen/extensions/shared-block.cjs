'use strict';
const fs = require('node:fs');
const { FileBase } = require('projen');

// Deliberately bounded experiment: preserves surrounding text while declared.
// It has no ownership history, adoption restoration, unmount sweep or transaction.
class SharedBlock extends FileBase {
  constructor(project, file, id, lines) {
    if (!/^[a-zA-Z0-9_.:/-]+$/.test(id)) throw new Error('Invalid block id');
    super(project, file, { readonly: false, marker: false, editGitignore: false });
    this.id = id;
    this.lines = [...new Set(lines)];
  }
  synthesizeContent() {
    const current = fs.existsSync(this.absolutePath) ? fs.readFileSync(this.absolutePath, 'utf8') : '';
    const eol = current.includes('\r\n') ? '\r\n' : '\n';
    const begin = '# cook:begin ' + this.id;
    const end = '# cook:end ' + this.id;
    const rows = current.split(/(?<=\n)/);
    const plain = row => row.replace(/\r?\n$/, '');
    const starts = rows.flatMap((row, index) => plain(row) === begin ? [index] : []);
    const ends = rows.flatMap((row, index) => plain(row) === end ? [index] : []);
    if (starts.length !== ends.length || starts.length > 1 || (starts.length && ends[0] <= starts[0]))
      throw new Error('Malformed managed block: ' + this.id);
    const block = [begin, ...this.lines, end, ''].join(eol);
    if (starts.length) return rows.slice(0, starts[0]).join('') + block + rows.slice(ends[0] + 1).join('');
    return current + (current && !current.endsWith('\n') ? eol : '') + block;
  }
}
module.exports = { SharedBlock };
