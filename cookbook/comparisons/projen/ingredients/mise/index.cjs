'use strict';
const { Component, TomlFile } = require('projen');

class Mise extends Component {
  constructor(project, sources) {
    super(project, 'mise');
    const tools = {};
    for (const source of sources) {
      for (const [name, version] of Object.entries(source.tools)) {
        if (tools[name] && tools[name] !== version) throw new Error('Conflicting tool: ' + name);
        tools[name] = version;
      }
    }
    this.file = new TomlFile(this, 'mise.toml', { obj: { tools } });
  }
}
module.exports = { Mise };
