@help('''
Writes chosen Claude Code settings and instructions from files and arguments you supply.

## Before cooking

The optional settings input is a JSON file in the destination. It is read as it exists before the invocation;
each top-level key becomes one owned setting.

## Cook

```sh
./cook dev.thecook:agents:0.1.0:claude-settings --settings=setup/claude.json --instructions='Run tests before committing.'
```

## Writes

| Target | Part | When |
| --- | --- | --- |
| `.claude/settings.json` | Each top-level key of the settings file, owned whole | `--settings` supplies keys |
| `CLAUDE.md` | The section `instructions` | `--instructions` supplies text |

Claude Code reads the project settings and instructions.

## With other recipes

Cook `dev.thecook:agents:0.1.0:instructions` after this recipe to use shared instructions in place of the
supplied instructions. Cook `dev.thecook:agents:0.1.0:permissions` after it to use the shared policy for
`permissions`; other settings keys remain separate targets. Select `--harness=claude` for either recipe.
''')
@yield('''
Review permission settings against Claude's permission model. Cook again with updated inputs to change
them.
''')
import dev.thecook.agents.Claude

@param(value = ['settings'], default = null, doc = 'JSON file whose top-level keys are written into .claude/settings.json')
File settings

@param(value = ['instructions'], default = null, doc = 'Instruction text for CLAUDE.md')
String instructions

@ingredient(Claude) def claude

if (settings) claude.settings(new JsonSlurper().parse(settings))
if (instructions) claude.instruct(instructions)
