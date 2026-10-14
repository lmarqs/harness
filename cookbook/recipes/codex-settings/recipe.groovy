@param(['config'], default = null, doc = 'TOML file whose top-level keys are written into .codex/config.toml')
File config

@param(['hooks'], default = null, doc = 'JSON file whose top-level keys are written into .codex/hooks.json')
File hooks

@param(['rules'], default = null, doc = 'Directory whose files become .codex/rules/<name>')
File rules

@param(['instructions'], default = null, doc = 'Codex-specific instruction text for AGENTS.md')
String instructions

@ingredient(Codex) def codex

if (config) codex.config(new TomlSlurper().parse(config))
if (hooks) codex.hooks(new JsonSlurper().parse(hooks))
if (rules) rules.eachFile { codex.rule(it.name, it.text) }
if (instructions) codex.instruct(instructions)
