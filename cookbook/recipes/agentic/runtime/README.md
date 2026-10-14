# agentic/runtime

Writes the Ralph TUI configuration and a runner script through the compiled `AgenticRuntime` recipe class, and a
`.gitignore` block that keeps Ralph's local and ephemeral files out of Git.

## Usage

```groovy
book.recipe('agentic/runtime', kitchen, [
    agent: 'codex', tracker: 'json', mode: 'standalone', enabled: true
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `id` | no | `agentic-runtime` | Identity of the composed recipe. |
| `agent` | no | `codex` | Agent that Ralph drives: `codex` or `claude`. |
| `tracker` | no | `json` | Task source: `json` for a PRD file, `beads` or `beads-bv`. |
| `mode` | no | `standalone` | `standalone` or `mise`. Selects the runner location. |
| `prdPath` | no | `prd.json` | PRD file for the `json` tracker: a relative repository path of letters, digits, `_`, `.`, `/` and `-`, without `..` segments. |
| `workers` | no | `1` | Parallel workers, an integer from 1 to 32. |
| `maxIterations` | no | `10` | Iteration limit, an integer from 0 to 1000. |
| `autoCommit` | no | `false` | Boolean. Value of `autoCommit` in the Ralph configuration. |
| `enabled` | no | `true` | `false` leaves out the Ralph configuration and runner; the `.gitignore` block is still written. |
| `adopt` | no | `false` | Boolean. `true` takes over existing unmanaged values at the Ralph configuration keys and an existing runner file. It never adopts the `.gitignore` block. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `.gitignore` | Ignore file | The block `git/ignore`. |
| `.ralph-tui/config.toml` | TOML | Top-level keys `configVersion`, `defaultAgent`, `tracker`, `trackerOptions`, `maxIterations`, `autoCommit` and `parallel`. |
| `.ralph-tui/run` in `standalone` mode, `.mise/tasks/ralph/run` in `mise` mode | Bash script, executable | The whole file. |

The `git/ignore` block lists `/.ralph-tui/worktrees/`, `/.ralph-tui/iterations/`, `/.ralph-tui/reports/`,
`/.ralph-tui/images/`, `/.ralph-tui/session.json`, `/.ralph-tui/session-meta.json`, `/.ralph-tui/progress.md` and
`/.ralph-tui/ralph.lock`.

The configuration holds `configVersion = "2.1"`, the agent as `defaultAgent`, the tracker, `trackerOptions` (`path`
set to `prdPath` for `json`, otherwise `beadsDir = ".beads"`), `maxIterations`, `autoCommit`, and `parallel` with
`mode` set to `never` for one worker or `auto` otherwise and `maxWorkers` set to `workers`.

The runner calls `ralph-tui run --agent <agent> --tracker <tracker>`. For `json` it passes its first argument, or
`prdPath` when none is given, as `--prd`. For the Beads trackers its first argument is a required epic ID, passed as
`--epic`. The remaining arguments are forwarded to Ralph.

## Inputs

The runner needs `ralph-tui` on the `PATH`, the selected agent's command-line tool, and the PRD file or Beads epic it
is given. The recipe installs nothing.

## Behaviour

Rendering rejects an unsupported `agent`, `tracker` or `mode`, `workers` or `maxIterations` outside its range, and an
invalid `prdPath`. `workers` and `maxIterations` must be integers or integer strings. `mise` mode only moves the
runner; the recipe declares no mise tools and writes no Beads configuration.

The recipe mounts the Git and Ralph contributors as the ingredient components `git` and `ralph-tui`. It declares no
removal scope.

The module compiles `thecook.cookbook.recipes.AgenticRuntime`, annotated `@Recipe('agentic-runtime')`.
`new AgenticRuntime(RalphConfig config, boolean enabled = true)` takes a
`thecook.cookbook.ingredients.ralphtui.RalphConfig` holding `agent`, `tracker`, `prdPath`, `mode`, `maxIterations`,
`workers`, `autoCommit` and `adopt`. `compose()` returns the Git contributor and, when `enabled` is `true`, the Ralph
contributor.

## Development

```sh
./gradlew -p cookbook/recipes/agentic/runtime check
./gradlew -p cookbook/recipes/agentic/runtime build
```

The module publishes `dev.thecook.recipes:agentic-runtime`, with its version in `gradle.properties`, and depends on
`dev.thecook.ingredients:git` and `dev.thecook.ingredients:ralph-tui`.
