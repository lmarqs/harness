'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { TextFile, JsonFile, TomlFile, SampleFile, FileBase, FutureComponent } = require('projen');
const { NodePackage, NodePackageManager } = require('projen/lib/javascript');
const { measure, project, write, read, inventory } = require('../lib/measure.cjs');

function nativeCases() {
  const cases = [];
  const add = (name, requirement, body) => cases.push(measure(name, 'native', requirement, body));
  add('native-implicit-git', 'Git is explicitly selected', root => {
    project(root).synth();
    return { satisfies: !fs.existsSync(path.join(root, '.gitignore')), observed: 'Base Project creates .gitignore and .gitattributes.', output: read(root, '.gitignore') };
  });
  add('native-default-declaration-purity', 'Declaration and preview do not change files', root => {
    write(root, 'package-lock.json', '{"lockfileVersion":3}\n');
    write(root, 'yarn.lock', '# durable Yarn lock\n');
    const before = inventory(root);
    new NodePackage(project(root), { packageManager: NodePackageManager.NPM, autoDetectBin: false });
    return { satisfies: JSON.stringify(inventory(root)) === JSON.stringify(before), observed: 'NodePackage default constructor deletes the other lockfile when its own lock exists, even with post-synthesis disabled.', yarn_lock_exists: fs.existsSync(path.join(root, 'yarn.lock')) };
  });
  add('native-disable-lock-cleanup', 'Public options preserve durable locks during declaration', root => {
    write(root, 'package-lock.json', '{"lockfileVersion":3}\n');
    write(root, 'yarn.lock', '# durable Yarn lock\n');
    const before = inventory(root);
    new NodePackage(project(root), { packageManager: NodePackageManager.NPM, deleteOrphanedLockFiles: false, autoDetectBin: false });
    return { satisfies: JSON.stringify(inventory(root)) === JSON.stringify(before), observed: 'deleteOrphanedLockFiles:false makes this declaration leave both files unchanged.' };
  });
  add('native-manual-gitignore', 'Retain manual content when a managed ignore contribution changes', root => {
    const before = '# team choice\r\nlocal-only/\r\n';
    write(root, '.gitignore', before);
    const p = project(root);
    p.gitignore.addPatterns('npm-debug.log*');
    p.synth();
    const after = read(root, '.gitignore');
    return { satisfies: after.includes(before), observed: 'IgnoreFile regenerates the whole file.', before, after };
  });
  add('native-manual-json', 'Retain unrelated native JSON keys', root => {
    const before = '{"manual":{"keep":true},"engines":{"node":">=18"}}\n';
    write(root, 'settings.json', before);
    const p = project(root);
    new JsonFile(p, 'settings.json', { obj: { engines: { node: '>=22' } }, readonly: false });
    p.synth();
    const after = read(root, 'settings.json');
    return { satisfies: JSON.parse(after).manual?.keep === true, observed: 'readonly:false allows editing but still replaces the declared JSON object.', before, after };
  });
  add('native-manual-toml', 'Retain unrelated TOML keys and comments', root => {
    const before = '# manual comment\n[tools]\npython = "3.12"\n';
    write(root, 'mise.toml', before);
    const p = project(root);
    new TomlFile(p, 'mise.toml', { obj: { tools: { node: '22' } } });
    p.synth();
    const after = read(root, 'mise.toml');
    return { satisfies: after.includes('# manual comment') && after.includes('python = "3.12"'), observed: 'TomlFile serializes the declared object as a whole file.', before, after };
  });
  add('native-adopt-restore', 'Restore the original adopted config after removing its component', root => {
    const before = '{"manual":"retain","node":"18"}\n';
    write(root, 'adopted.json', before);
    const p = project(root);
    new JsonFile(p, 'adopted.json', { obj: { node: '22' } });
    p.synth();
    project(root).synth();
    const exists = fs.existsSync(path.join(root, 'adopted.json'));
    return { satisfies: exists && read(root, 'adopted.json') === before, observed: 'A preexisting file is overwritten, then deleted when its managed component is absent; original bytes are not restored.', before, after: exists ? read(root, 'adopted.json') : null };
  });
  add('native-edited-file-removal', 'Refuse to delete a managed file edited by hand', root => {
    const p = project(root);
    new TextFile(p, 'owned.conf', { lines: ['generated=true'] });
    p.synth();
    write(root, 'owned.conf', read(root, 'owned.conf') + 'manual=true\n');
    const before = read(root, 'owned.conf');
    project(root).synth();
    const exists = fs.existsSync(path.join(root, 'owned.conf'));
    return { satisfies: exists, observed: 'Orphan cleanup removes the whole previously tracked file despite the manual addition.', before, after: exists ? read(root, 'owned.conf') : null };
  });
  add('native-managed-drift', 'Report a manual change to managed output before overwriting it', root => {
    const p = project(root);
    new TextFile(p, 'owned.conf', { lines: ['generated=true'] });
    p.synth();
    write(root, 'owned.conf', 'generated=manual\n');
    const next = project(root);
    new TextFile(next, 'owned.conf', { lines: ['generated=new'] });
    let refused = false;
    try { next.synth(); } catch { refused = true; }
    return { satisfies: refused && read(root, 'owned.conf') === 'generated=manual\n', observed: 'Synthesis regenerates edited output without an ownership-hash diagnostic.', after: read(root, 'owned.conf') };
  });
  add('native-sample-file', 'Seed files without claiming future edits', root => {
    write(root, 'sample.conf', 'manual=true\n');
    const p = project(root);
    new SampleFile(p, 'sample.conf', { contents: 'generated=true\n' });
    p.synth();
    return { satisfies: read(root, 'sample.conf') === 'manual=true\n', observed: 'SampleFile preserves existing files; it cannot later maintain selected keys.', after: read(root, 'sample.conf') };
  });
  add('native-duplicate-writer', 'Reject two components writing the same path', root => {
    const p = project(root);
    new JsonFile(p, 'config.json', { obj: { one: true } });
    let diagnostic;
    try { new JsonFile(p, 'config.json', { obj: { two: true } }); } catch (error) { diagnostic = error.message; }
    return { satisfies: Boolean(diagnostic), observed: diagnostic };
  });
  add('native-readonly-false-removal', 'Remove a contribution when its editable file component is absent', root => {
    const p = project(root);
    new JsonFile(p, 'editable.json', { obj: { generated: true }, readonly: false });
    p.synth();
    project(root).synth();
    const remains = fs.existsSync(path.join(root, 'editable.json'));
    return { satisfies: !remains, observed: 'Editable files are omitted from the current cleanup manifest and remain after component removal.', file_remains: remains };
  });
  add('native-cross-file-rollback', 'Restore the before state if a later file synthesis fails', root => {
    write(root, 'first.conf', 'old=true\n');
    const p = project(root);
    new TextFile(p, 'first.conf', { lines: ['new=true'] });
    class BrokenFile extends FileBase {
      synthesizeContent() { throw new Error('injected second-file failure'); }
    }
    new BrokenFile(p, 'second.conf');
    let error;
    try { p.synth(); } catch (failure) { error = failure.message; }
    return { satisfies: error === 'injected second-file failure' && read(root, 'first.conf') === 'old=true\n', observed: 'Earlier file writes remain after a later synthesis failure.', error_message: error, first_file_after: read(root, 'first.conf') };
  });
  add('native-future-attachment', 'Support declaration before attachment to a project', root => {
    class Tool extends FutureComponent {
      constructor() { super(); this.options = { node: '22' }; }
      init() { new JsonFile(this, 'future.json', { obj: this.options }); }
    }
    const tool = new Tool();
    const detached = inventory(root);
    const p = project(root);
    tool.attach(p, 'future-node');
    p.synth();
    return { satisfies: Object.keys(detached).length === 0 && JSON.parse(read(root, 'future.json')).node === '22', observed: 'FutureComponent.attach provides a native deferred attachment mechanism.' };
  });
  return cases;
}
module.exports = { nativeCases };
