'use strict';
const { Component } = require('projen');

class Git extends Component {
  constructor(project, sources) {
    super(project, 'git-consumer');
    project.gitignore.addPatterns(...new Set(sources.flatMap(source => source.files.ephemeral)));
  }
}
module.exports = { Git };
