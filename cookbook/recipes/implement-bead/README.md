# implement-bead

Sets up Ralph with the Beads tracker in mise mode and declares a `ralph:bead` mise task that shows one Beads issue and
then runs a command that works on it.

## Usage

```groovy
book.recipe('implement-bead', kitchen, [
    issue: 'example-123', runCommand: './run-selected-issue example-123'
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `issue` | yes | none | Beads issue ID. An empty value is rejected. |
| `runCommand` | yes | none | Command the task runs after `bd show`. It must contain the `issue` text; otherwise the recipe rejects it. |
| `id` | no | `implement-bead` | Component ID of the recipe. It is not forwarded. |
| `mise` | no | the mise component agentic/ralph creates | Existing mise component that receives the Ralph tools and the task. |
| `adopt` | no | `false` | Forwarded to agentic/ralph, which accepts boolean `true`/`false` or string `'true'`/`'false'` and applies it to the Ralph and Beads outputs and to the mise component it creates, which holds the task. |
| Other [agentic/ralph](../agentic/ralph/README.md) arguments | no | as in agentic/ralph | Forwarded unchanged, except that `tracker` is always `beads` and `mode` is always `mise`. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `mise.toml`, or the file of the supplied `mise` component | TOML | The `tasks."ralph:bead"` key: `{ run = "bd show '<issue>' && <runCommand>" }`. |
| `.ralph-tui/config.toml`, `.mise/tasks/ralph/run`, `.beads/config.yaml` and the Ralph tools in `mise.toml` | As in agentic/ralph | The parts the composed agentic/ralph recipe owns for `tracker: 'beads'` and `mode: 'mise'`. |

## Inputs

Running the task needs mise, the Beads CLI `bd`, and the tools that `runCommand` calls. `runCommand` runs only when
`bd show` succeeds for the issue.

## Behaviour

- The recipe composes [agentic/ralph](../agentic/ralph/README.md) with every argument except `id`, overriding
  `tracker` and `mode`.
- The task shell-quotes the issue in single quotes and inserts `runCommand` as given.
- The recipe writes the task declaration and the Ralph setup only; it does not run the task or start Ralph.
