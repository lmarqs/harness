'use strict';
const { Component } = require('projen');
const { NodePackageManager } = require('projen/lib/javascript');

class Yarn extends Component {
  constructor(project, { version = '4.17.0' } = {}) {
    super(project, 'yarn');
    this.version = version;
    this.kind = NodePackageManager.YARN_BERRY;
    this.tools = { 'npm:yarn': version };
    this.files = { ephemeral: ['yarn-debug.log*', 'yarn-error.log*'], durable: ['yarn.lock'] };
    this.packageManager = 'yarn@' + version;
  }
}
module.exports = { Yarn };
