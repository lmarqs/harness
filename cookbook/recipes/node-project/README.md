# node-project

Sets up a Node project with npm or Yarn: tool versions in `mise.toml`, the engine and package manager in
`package.json`, setup and lock tasks, and Git ignores for what those tools leave behind.

```sh
./cook dev.thecook:node-project --manager=yarn
```

| Target | Part |
| --- | --- |
| `package.json` | `engines.node` and `packageManager` |
| `mise.toml` | `tools.node`, and `tools."npm:npm"` or `tools.yarn` |
| `.mise/tasks/node/setup`, `.mise/tasks/node/lock` | The whole executable scripts |
| `.gitignore` | The section `ignore`: the manager's ephemeral files |
