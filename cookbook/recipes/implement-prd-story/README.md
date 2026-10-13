# Implement a PRD story

Select one story from an existing JSON PRD and compose Ralph with the JSON tracker.

```groovy
book.recipe('implement-prd-story', kitchen, [
    prdPath: 'prd.json', storyId: 'US-001'
]).mount()
```

Required options are `prdPath` and `storyId`. The JSON file must contain a `userStories` list with exactly one story whose `id` equals the supplied ID.

Optional `selectedPrdPath` defaults to `.ralph-tui/selected-prd.json`; the selected PRD preserves other top-level values and contains only that story. `adopt: true` permits adopting that output file. `id` defaults to `implement-prd-story`. Remaining options are forwarded to [agentic/ralph](../agentic/ralph/README.md), using `tracker: 'json'`, the selected PRD path, and a default `mode` of `mise`.

See [factory usage](../README.md).
