package thecook.cookbook.ingredients.ralphtui

import thecook.annotations.Ingredient
import thecook.composition.ComponentId
import thecook.filesystem.TargetPath
import thecook.model.*
import static thecook.model.FileReference.*

@Ingredient('ralph-tui')
class Ralph implements Contributor {
    final ComponentId id
    final RalphConfig config
    final RuntimeFiles files = new RuntimeFiles(
        FileSet.of(directory('.ralph-tui/worktrees'), directory('.ralph-tui/iterations'),
            directory('.ralph-tui/reports'), directory('.ralph-tui/images'),
            file('.ralph-tui/session.json'), file('.ralph-tui/session-meta.json'), file('.ralph-tui/progress.md')),
        FileSet.of(file('.ralph-tui/ralph.lock')))

    Ralph(RalphConfig config, String name = 'ralph-tui') { this.config = config; this.id = ComponentId.of(name) }

    List<OutputSpec> outputs() {
        config.validate()
        List<KeyContribution> keys = []
        for (String name : config.nativeConfig().keySet()) {
            String key = name
            Provider<Object> value = { config.nativeConfig().get(key) }
            keys.add(new KeyContribution([key], value, config.adopt))
        }
        Provider<String> runner = { launcher() }
        String path = config.mode == 'mise' ? '.mise/tasks/ralph/run' : '.ralph-tui/run'
        [new StructuredOutput(id, TargetPath.of('.ralph-tui/config.toml'), DocumentFormat.TOML, keys),
         new FileOutput(id, TargetPath.of(path), runner, true, config.adopt)]
    }

    private String launcher() {
        String trackerArgument = config.tracker == 'json' ? '--prd' : '--epic'
        String source = config.tracker == 'json' ? '${1:-' + config.prdPath + '}' : '${1:?Supply a Beads epic ID}'
        '''#!/usr/bin/env bash
set -euo pipefail
SOURCE="''' + source + '''"
if [[ $# -gt 0 ]]; then shift; fi
exec ralph-tui run --agent ''' + config.agent + ' --tracker ' + config.tracker + ' ' + trackerArgument + ''' "$SOURCE" "$@"
'''
    }
}
