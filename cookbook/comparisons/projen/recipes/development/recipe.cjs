'use strict';
const { Project, LogLevel } = require('projen');
const { NodePackage } = require('projen/lib/javascript');
const { NodeRuntime } = require('../../ingredients/node');
const { Npm } = require('../../ingredients/npm');
const { Yarn } = require('../../ingredients/yarn');
const { Mise } = require('../../ingredients/mise');
const { Git } = require('../../ingredients/git');

function makeRecipe({ outdir, manager = 'npm', nodeVersion = '22', git = true, typesNode = false } = {}) {
  if (!['npm', 'yarn'].includes(manager)) throw new Error('Package manager must be npm or yarn');
  if (!/^\d+(\.\d+){0,2}$/.test(String(nodeVersion))) throw new Error('Node version must be numeric');
  const project = new Project({ name: 'cook-example', outdir, logging: { level: LogLevel.OFF } });
  const node = new NodeRuntime(project, { version: nodeVersion, typesNode });
  const selected = manager === 'npm' ? new Npm(project) : new Yarn(project);
  const pkg = new NodePackage(project, {
    packageManager: selected.kind,
    minNodeVersion: node.major + '.0.0',
    yarnBerryOptions: { version: '4.17.0', yarnrcOptions: { nodeLinker: 'node-modules' } },
    deleteOrphanedLockFiles: false,
    autoDetectBin: false,
    licensed: false,
  });
  pkg.addField('packageManager', selected.packageManager);
  // This recipe owns Yarn configuration; readonly files enter projen's cleanup manifest.
  const yarnrc = project.tryFindFile('.yarnrc.yml');
  if (yarnrc) yarnrc.readonly = true;
  node.configure(pkg);
  new Mise(project, [node, selected]);
  if (git) new Git(project, [node, selected]);
  else project.tryRemoveFile('.gitignore');
  return project;
}
module.exports = { makeRecipe };
