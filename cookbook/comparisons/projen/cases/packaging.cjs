'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { measure, read, write } = require('../lib/measure.cjs');

function execute(args, cwd, env = {}) {
  const result = spawnSync(process.execPath, args, {
    cwd, encoding: 'utf8', timeout: 30000,
    env: { ...process.env, PATH: path.dirname(process.execPath) + path.delimiter + process.env.PATH, ...env },
  });
  if (result.error || result.status !== 0) throw new Error(result.error?.message || result.stderr || result.stdout);
  return result.stdout;
}
function packagingCases() {
  return [measure('independent-package', 'authored-components', 'Upgrade a separately versioned ingredient without rebuilding the engine', root => {
    const npmCli = path.resolve(path.dirname(process.execPath), '../lib/node_modules/npm/bin/npm-cli.js');
    const source = path.resolve(__dirname, '../ingredients/npm');
    const consumer = path.join(root, 'consumer');
    const cache = process.env.npm_config_cache || '/tmp/the-cook-projen-cache';
    const tarballs = path.join(root, 'tarballs');
    fs.mkdirSync(tarballs);
    fs.mkdirSync(consumer);
    function pack(directory) {
      const output = execute([npmCli, 'pack', directory, '--pack-destination', tarballs, '--offline', '--ignore-scripts', '--json', '--cache', cache], root);
      return path.join(tarballs, JSON.parse(output)[0].filename);
    }
    // Supply installed peers as local artifacts: an empty npm cache must work.
    const peers = ['constructs', 'projen'].map(name => pack(path.dirname(require.resolve(name + '/package.json'))));
    function install(artifact) {
      execute([npmCli, 'install', ...peers, artifact, '--prefix', consumer, '--offline', '--ignore-scripts', '--no-audit', '--no-fund', '--cache', cache], root);
    }
    function synth() {
      const source = [
        "const path = require('node:path');",
        "const root = process.argv[1];",
        "const {Project,LogLevel} = require(path.join(root,'node_modules/projen'));",
        "const {Npm} = require(path.join(root,'node_modules/@the-cook/projen-probe-npm'));",
        "const p = new Project({name:'package-consumer',outdir:path.join(root,'project'),logging:{level:LogLevel.OFF}});",
        "p.gitignore.addPatterns(...new Npm(p).files.ephemeral); p.synth();",
      ].join('\n');
      execute(['-e', source, consumer], root, { DEBUG: 'false', PROJEN_DISABLE_POST: 'true' });
      return read(path.join(consumer, 'project'), '.gitignore');
    }
    install(pack(source));
    const before = synth();
    const v2 = path.join(root, 'npm-v2');
    fs.cpSync(source, v2, { recursive: true });
    const manifest = JSON.parse(read(v2, 'package.json'));
    manifest.version = '0.2.0';
    write(v2, 'package.json', JSON.stringify(manifest, null, 2) + '\n');
    write(v2, 'index.cjs', read(v2, 'index.cjs').replace("['npm-debug.log*']", "['npm-debug.log*', 'npm-extra.log*']"));
    install(pack(v2));
    const after = synth();
    const installed = JSON.parse(read(consumer, 'node_modules/@the-cook/projen-probe-npm/package.json')).version;
    return { satisfies: installed === '0.2.0' && !before.includes('npm-extra.log*') && after.includes('npm-extra.log*'), observed: 'Two real npm pack/install cycles consume versions 0.1.0 and 0.2.0 offline; installed projen and constructs peers are supplied as local tarballs, so no prefilled registry cache is required.', installed_ingredient_version: installed, before, after };
  })];
}
module.exports = { packagingCases, execute };
