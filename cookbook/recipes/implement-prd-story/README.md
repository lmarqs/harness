# Implement a PRD story

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Select one story from an existing JSON PRD and compose Ralph with the JSON tracker.

```groovy
book.recipe('implement-prd-story', kitchen, [
    prdPath: 'prd.json', storyId: 'US-001'
]).mount()
```

Required options are `prdPath` and `storyId`. The JSON file must exist in the destination and contain a `userStories` list with exactly one story whose `id`
equals the supplied ID. Supply that file yourself; it is not created by this recipe. The example assumes initialized
Groovy composition and absent or already managed selected output.

Optional `selectedPrdPath` defaults to `.ralph-tui/selected-prd.json`; the selected PRD preserves other top-level values and contains only that story. Boolean `adopt: true` in Groovy composition permits adopting that output file; CLI `--adopt=true` is a string and
does not satisfy the whole-file check. Managed edits still fail reconciliation. `id` defaults to `implement-prd-story`. Remaining options are forwarded to [agentic/ralph](../agentic/ralph/README.md), using `tracker: 'json'`, the selected PRD path, and a default `mode` of `mise`.

Cooking writes the selected PRD and Ralph configuration/runner; it does not execute or complete the story. Running
the runner separately requires Ralph, the selected agent and tracker tools, and mise when using mise mode.
Writing tool declarations does not install them. See [factory usage](../README.md).
