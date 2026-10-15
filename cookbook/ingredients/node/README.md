# node

Models a Node project: `package.json`, the package manager and the files the toolchain leaves behind. One instance
per project directory; a workspace package is an instance `at` its directory.

## Model

- `version` is the Node major or exact version; without it, `engines.node` is left alone.
- `manager(name)` selects npm, pnpm or Yarn.
- `pinManager(version)` pins the selected manager, at a default version per manager when none is given.
- `dependency(package, version, kind)` adds one dependency.
- `dropVersionFile()` removes `.nvmrc` once mise owns the version.
- `tools()`, `ephemeralFiles()`, `setupScript()`, `lockScript()` and `installCommand()` expose facts for mise and Git;
  the install command runs in the project directory.

## Writes

| Target | Part |
| --- | --- |
| `package.json` | `engines.node` when a version was set, `packageManager` when a manager was pinned, and `<kind>.<package>` per added dependency, each owned on its own |
| `.nvmrc` | Deleted when `dropVersionFile()` was called |

The scripts `npm/setup.sh`, `npm/lock.sh`, `yarn/setup.sh` and `yarn/lock.sh` ship inside this artifact.
