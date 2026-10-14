# node

Models a Node project: `package.json`, the package manager and the files the toolchain leaves behind. One instance
per project directory; a workspace package is an instance `at` its directory.

## Model

- `version` is the Node major or exact version.
- `manager(name, version)` selects npm, pnpm or Yarn, with a default version per manager.
- `dependency(package, version, kind)` adds one dependency.
- `dropVersionFile()` removes `.nvmrc` once mise owns the version.
- `tools()`, `ephemeralFiles()`, `setupScript()`, `lockScript()` and `installCommand()` expose facts for mise and Git.

## Writes

| Target | Part |
| --- | --- |
| `package.json` | `engines.node`, `packageManager`, and each added dependency key, owned on its own |
| `.nvmrc` | Deleted when `dropVersionFile()` was called |

The scripts `npm/setup.sh`, `npm/lock.sh`, `yarn/setup.sh` and `yarn/lock.sh` ship inside this artifact.
