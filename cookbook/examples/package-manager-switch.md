# Switching the package manager

This is a **proposed interaction**, not a working CLI. The goal is to make the repository's package manager a single choice, rather than accumulate separate `npm` and `ignore-yarn-lock` selections.

Start with Git, Node, and npm:

```sh
the-cook init
./cook ingredient add git node@20 npm
./cook kitchen apply
```

Later, switch to Yarn in two cook commands:

```sh
./cook ingredient use yarn
./cook kitchen apply
```

`use yarn` replaces `npm` in the `package-manager` slot. `kitchen apply` removes npm's own `.gitignore` lines and writes Yarn's lines. Running it again produces no new changes. Switching back uses `./cook ingredient use npm` followed by `./cook kitchen apply`.

The complete switch must also handle `package.json` package-manager metadata, dependency commands in files the kitchen manages, and the lockfile. An ignored, already-tracked `yarn.lock` remains tracked. A new `yarn.lock` needs a package-manager operation to generate it; the kitchen cannot derive its contents from a template. The current `files.append` sketch does not define those steps, so the two commands above are the desired interface, **not yet a complete migration design**. The tool should show the pending changes and stop on edits it cannot safely reconcile, rather than claim the repository has switched when its lockfile or CI still says npm.

This is a change of repository configuration. It does not require a station, which supplies starting content, or a session recipe, which runs a named work session. If migration needs an executable action, that action and its safety rules still need a design.
