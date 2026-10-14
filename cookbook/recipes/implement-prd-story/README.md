# implement-prd-story

Writes a copy of a JSON PRD reduced to one user story, where Ralph will read it. Apply it with
[ralph](../ralph/README.md) pointed at that copy:

```sh
./cook dev.thecook:implement-prd-story --story=US-007 \
       dev.thecook:ralph --prd=.ralph-tui/selected-prd.json --mode=mise
```

| Target | Part |
| --- | --- |
| `.ralph-tui/selected-prd.json` | The whole file: the source PRD with `userStories` reduced to the selected story |
