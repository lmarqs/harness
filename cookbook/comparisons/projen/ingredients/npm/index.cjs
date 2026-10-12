'use strict';
const { Component } = require('projen');
const { NodePackageManager } = require('projen/lib/javascript');

class Npm extends Component {
  constructor(project, { version = '10.9.8' } = {}) {
    super(project, 'npm');
    this.version = version;
    this.kind = NodePackageManager.NPM;
    this.tools = { npm: version };
    this.files = { ephemeral: ['npm-debug.log*'], durable: ['package-lock.json'] };
    this.packageManager = 'npm@' + version;
  }
}
module.exports = { Npm };
