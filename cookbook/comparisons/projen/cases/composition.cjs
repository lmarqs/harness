'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { makeRecipe } = require('../recipes/development/recipe.cjs');
const { measure, write, read, inventory } = require('../lib/measure.cjs');

function compositionCases() {
  const cases = [];
  const add = (name, requirement, body) => cases.push(measure(name, 'authored-components', requirement, body));
  add('composition', 'Connect independent exports to one writer', root => {
    makeRecipe({ outdir: root, nodeVersion: '20', typesNode: true }).synth();
    const ignore = read(root, '.gitignore');
    return { satisfies: ignore.split('\n').filter(row => row === 'node_modules/').length === 1 && ignore.includes('npm-debug.log*'), observed: 'Five small components compose through ordinary objects and projen file primitives.', ignore };
  });
  add('node-version-switch', 'Node 20 to 22 updates package metadata, types and mise', root => {
    makeRecipe({ outdir: root, nodeVersion: '20', typesNode: true }).synth();
    makeRecipe({ outdir: root, nodeVersion: '22', typesNode: true }).synth();
    const pkg = JSON.parse(read(root, 'package.json'));
    return { satisfies: pkg.engines.node === '>= 22.0.0' && pkg.devDependencies['@types/node'] === '^22.0.0' && read(root, 'mise.toml').includes('node = "22"'), observed: 'One configuration value updates the three declarations.', package: pkg, mise: read(root, 'mise.toml') };
  });
  add('manager-switch', 'npm to Yarn and back updates config and preserves durable lockfiles', root => {
    write(root, 'package-lock.json', '{"lockfileVersion":3}\n');
    write(root, 'yarn.lock', '# durable Yarn lock\n');
    makeRecipe({ outdir: root, manager: 'npm' }).synth();
    makeRecipe({ outdir: root, manager: 'yarn' }).synth();
    const yarn = JSON.parse(read(root, 'package.json')).packageManager;
    const yarnTasks = read(root, '.projen/tasks.json');
    makeRecipe({ outdir: root, manager: 'npm' }).synth();
    return { satisfies: yarn === 'yarn@4.17.0' && yarnTasks.includes('yarn install --immutable') && JSON.parse(read(root, 'package.json')).packageManager === 'npm@10.9.8' && !fs.existsSync(path.join(root, '.yarnrc.yml')) && read(root, 'package-lock.json') === '{"lockfileVersion":3}\n' && read(root, 'yarn.lock') === '# durable Yarn lock\n', observed: 'Public lock-cleanup option and readonly Yarn-config setting support safe template switching; package installation/lockfile regeneration is not exercised.', yarn_package_manager: yarn, npm_package: JSON.parse(read(root, 'package.json')) };
  });
  add('explicit-git-adapter', 'Node and mise do not implicitly materialize Git output', root => {
    makeRecipe({ outdir: root, git: false }).synth();
    return { satisfies: !fs.existsSync(path.join(root, '.gitignore')), observed: 'Removing the base Project Git writer makes Git selection explicit.', files: Object.keys(inventory(root)) };
  });
  add('declaration-with-public-options', 'Declare this recipe without changing the target', root => {
    write(root, 'package-lock.json', '{"lockfileVersion":3}\n');
    write(root, 'yarn.lock', '# durable Yarn lock\n');
    const before = inventory(root);
    makeRecipe({ outdir: root });
    return { satisfies: JSON.stringify(inventory(root)) === JSON.stringify(before), observed: 'The recipe disables constructor lockfile cleanup.' };
  });
  add('repeat-idempotence', 'Repeat synthesis leaves bytes and modes unchanged', root => {
    makeRecipe({ outdir: root }).synth();
    const before = inventory(root);
    makeRecipe({ outdir: root }).synth();
    return { satisfies: JSON.stringify(inventory(root)) === JSON.stringify(before), observed: 'Repeated synthesis is byte/mode idempotent for this recipe.' };
  });
  return cases;
}
module.exports = { compositionCases };
