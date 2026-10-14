# development

Sets up a Node project with npm or Yarn: tool versions in `mise.toml`, the engine and package manager in
`package.json`, setup and lock tasks, and Git ignores for the files those tools leave behind.

## Usage

```groovy
book.recipe('development', kitchen, [
    manager: 'npm', nodeVersion: '22', adopt: true
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `manager` | no | `npm` | Package manager: `npm` or `yarn`. Other values are rejected. |
| `nodeVersion` | no | `22` | Node version for mise: one to three numeric parts, such as `22` or `20.11`. |
| `npmVersion` | no | `10.9.8` | npm version, used when `manager` is `npm`. It must be an exact `major.minor.patch` version. |
| `yarnVersion` | no | `4.17.0` | Yarn version, used when `manager` is `yarn`. It must be an exact `major.minor.patch` version. |
| `adopt` | no | `false` | Boolean `true` or string `'true'` lets the recipe adopt existing unmanaged `package.json` keys, `mise.toml` keys and task files. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `package.json` | JSON | `engines.node` (`^<major>.0.0`), `packageManager` (`npm@<version>` or `yarn@<version>`), and `devDependencies.@types/node` (`^<major>.0.0`) only when that key already exists. |
| `mise.toml` | TOML | `tools.node`, and `tools."npm:npm"` or `tools.yarn`. |
| `.mise/tasks/node/setup` | Executable Bash script | The whole file: the `node:setup` task, which runs `npm ci` or `yarn install --immutable`. |
| `.mise/tasks/node/lock` | Executable Bash script | The whole file: the `node:lock` task, which runs `npm install --package-lock-only` or `yarn install`. |
| `.gitignore` | Ignore file | The `git/ignore` block: `node_modules/` and `npm-debug.log*` for npm; `node_modules/`, `yarn-error.log*`, `/.yarn/install-state.gz` and `/.yarn/unplugged/` for Yarn. |

## Inputs

Running the tasks needs mise. `node:setup` exits with an error when the lockfile is missing: `package-lock.json` for
npm, `yarn.lock` for Yarn.

## Behaviour

- The recipe composes the `mise`, `node`, `npm` or `yarn`, and `git` ingredients.
- The version argument of the manager that is not selected is ignored.
- `adopt` does not apply to the `.gitignore` block.
- The recipe writes declarations only; it installs nothing and does not run the tasks.
