# agentic/ralph

Writes the Ralph TUI configuration and a runner script, and composes the Beads tracker and mise when the selected
tracker and mode need them. Running the generated script starts Ralph over a PRD file or a Beads epic.

## Usage

```groovy
book.recipe('agentic/ralph', kitchen, [
    agent: 'codex', tracker: 'json', mode: 'mise', prdPath: 'prd.json'
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `id` | no | `agentic-ralph` | Identity of the composed recipe. |
| `agent` | no | `codex` | Agent that Ralph drives: `codex` or `claude`. |
| `tracker` | no | `json` | Task source: `json` for a PRD file, `beads` or `beads-bv`. |
| `mode` | no | `standalone` | `standalone` or `mise`. Selects the runner location and whether mise is composed. |
| `workers` | no | `1` | Parallel workers, an integer from 1 to 32. |
| `maxIterations` | no | `10` | Iteration limit, an integer from 0 to 1000. |
| `prdPath` | no | `prd.json` | PRD file for the `json` tracker: a relative repository path of letters, digits, `_`, `.`, `/` and `-`, without `..` segments. |
| `headless` | no | `false` | Boolean. `true` adds `--headless` to the runner command. |
| `autoCommit` | no | `false` | Boolean. Value of `autoCommit` in the Ralph configuration. |
| `model` | no | none | Added as `model` to the agent options. |
| `agentOptions` | no | empty map | Map written as the agent's `options`. |
| `issuePrefix` | no | none | Beads issue prefix, used only by the `beads` and `beads-bv` trackers. |
| `mise` | no | none | Existing mise component that receives Ralph's tool declarations in `mise` mode. Without it the recipe composes a new mise ingredient. |
| `adopt` | no | `false` | Boolean. `true` takes over existing unmanaged values at the Ralph configuration keys, the runner file, and the keys of the Beads and mise ingredients the recipe creates. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `.ralph-tui/config.toml` | TOML | Top-level keys `configVersion`, `defaultAgent`, `tracker`, `agents`, `trackerOptions`, `maxIterations`, `autoCommit` and `parallel`. |
| `.ralph-tui/run` in `standalone` mode, `.mise/tasks/ralph/run` in `mise` mode | Bash script, mode `0755` | The whole file. |
| `.beads/config.yaml` | YAML | For `beads` and `beads-bv`: the key `json` (`true`), and `issue-prefix` when `issuePrefix` is set. |
| `mise.toml` | TOML | In `mise` mode, through the created mise ingredient: the keys `tools.bun` (`1.3.6`) and `tools."npm:ralph-tui"` (`0.12.0`). A supplied `mise` component receives the same tools instead. |

The configuration holds `configVersion = "2.1"`, the agent as `defaultAgent` and as the single `agents` entry (with
`name`, `plugin` and `options`), the tracker, `trackerOptions` (`path` set to `prdPath` for `json`, otherwise
`beadsDir = ".beads"`), `maxIterations`, `autoCommit`, and `parallel` with `mode` set to `never` for one worker or
`auto` otherwise and `maxWorkers` set to `workers`.

The runner calls `ralph-tui run --agent <agent> --tracker <tracker>`. For `json` it passes its first argument, or
`prdPath` when none is given, as `--prd`. For the Beads trackers its first argument is a required epic ID, passed as
`--epic`. The remaining arguments are forwarded to Ralph.

## Inputs

The runner needs `ralph-tui` on the `PATH`, the selected agent's command-line tool, and the PRD file or Beads epic it
is given. The default `json` tracker needs no other tool; the `beads` and `beads-bv` trackers need `bd`, and `beads-bv`
uses `bv` when it is installed. In `mise` mode, the mise ingredient declares `npm:ralph-tui` and `bun` as tools; the
recipe installs nothing.

## Behaviour

The recipe rejects an unsupported `agent`, `tracker` or `mode`, `workers` or `maxIterations` outside its range or not
an integer, an invalid `prdPath`, `headless`, `autoCommit` or `adopt` values other than `true` and `false`, and an
`agentOptions` value that is not a map. Option keys outside the table are ignored.

The returned recipe exposes `ralph`, the Ralph ingredient component, with the exports `tools`, `files.local` (the
`.ralph-tui/` session files, progress file and iteration, worktree, report and image directories) and
`files.ephemeral` (`.ralph-tui/ralph.lock`). It also exposes `beads` for the Beads trackers and `mise` in `mise` mode.
The recipe writes no ignore rules for those files. It declares no removal scope.

The recipe is composed by [implement-bead](../../implement-bead/README.md) and
[implement-prd-story](../../implement-prd-story/README.md).
