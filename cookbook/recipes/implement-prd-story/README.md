# implement-prd-story

Writes a copy of a JSON PRD reduced to one user story and sets up Ralph with the JSON tracker pointing at that copy.

## Usage

```groovy
book.recipe('implement-prd-story', kitchen, [
    prdPath: 'prd.json', storyId: 'US-001'
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `prdPath` | yes | none | Repository-relative path of an existing JSON PRD. |
| `storyId` | yes | none | ID of the story to select. |
| `selectedPrdPath` | no | `.ralph-tui/selected-prd.json` | Repository-relative path of the reduced PRD. It is passed to agentic/ralph as its `prdPath`, so it must satisfy that recipe's path rule. |
| `mode` | no | `mise` | Forwarded to agentic/ralph: `mise` or `standalone`. |
| `id` | no | `implement-prd-story` | Component ID of the recipe. It is not forwarded. |
| `adopt` | no | `false` | Boolean `true` lets the recipe adopt an existing unmanaged file at `selectedPrdPath`; string `'true'` does not. The value is also forwarded to agentic/ralph, which accepts boolean `true`/`false` or string `'true'`/`'false'`. |
| Other [agentic/ralph](../agentic/ralph/README.md) arguments | no | as in agentic/ralph | Forwarded unchanged, except that `tracker` is always `json` and `prdPath` is always `selectedPrdPath`. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `selectedPrdPath` | JSON, pretty-printed | The whole file: every top-level value of the source PRD, with `userStories` reduced to the selected story. |
| `.ralph-tui/config.toml`, the runner (`.mise/tasks/ralph/run` in `mise` mode, `.ralph-tui/run` in `standalone` mode) and, in `mise` mode, the Ralph tools in `mise.toml` | As in agentic/ralph | The parts the composed agentic/ralph recipe owns for `tracker: 'json'`. |

## Inputs

The file at `prdPath`. It is a JSON object with a `userStories` list that contains exactly one story whose `id` equals
`storyId`.

## Behaviour

- The recipe rejects a missing PRD file, a PRD that is not a JSON object with a `userStories` list, and a PRD with zero
  or several stories matching `storyId`.
- The source PRD is read and never modified.
- The recipe composes [agentic/ralph](../agentic/ralph/README.md) with every argument except `id`, overriding
  `tracker`, `prdPath` and the `mode` default.
- The recipe writes files only; it does not start Ralph or work on the story.
