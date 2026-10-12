'use strict';
const { Component } = require('projen');

class NodeRuntime extends Component {
  constructor(project, { version = '22', typesNode = false } = {}) {
    super(project, 'node-runtime');
    this.version = String(version);
    if (!/^\d+(\.\d+){0,2}$/.test(this.version)) throw new Error('Node version must be numeric');
    this.major = this.version.split('.')[0];
    this.tools = { node: this.version };
    this.files = { ephemeral: ['node_modules/'], durable: [] };
    this.typesNode = typesNode;
  }
  configure(nodePackage) {
    if (this.typesNode) nodePackage.addDevDeps('@types/node@^' + this.major + '.0.0');
  }
}
module.exports = { NodeRuntime };
