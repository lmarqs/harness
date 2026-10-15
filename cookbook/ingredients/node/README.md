# node

A Node project: `package.json`, its package manager and the files the toolchain leaves behind.

## Writes for

| Program | Format or version |
| --- | --- |
| Node | `engines.node` as a caret range on the major you set |
| npm, pnpm, Yarn | `packageManager` as Corepack reads it; defaults npm 10.9.8, pnpm 10.4.1, Yarn 4.17.0 |
| mise | tool names `node`, `npm:npm`, `pnpm` and `yarn` |

## Instances

One instance per project directory. A workspace package is a second instance `at` its directory, and each owns its
own `package.json`.

## Use

```groovy
import dev.thecook.Node

@ingredient(Node) def node

node.version = '22'
node.manager('yarn')
node.pinManager()
mise.tools(node.tools())
mise.script('node:setup', node.setupScript())
git.ignore(node.ephemeralFiles())
```

## With other models

`tools()` is for mise, `ephemeralFiles()` for Git, and `setupScript()`, `lockScript()` and `installCommand()` for a
task runner. This model writes none of those; the recipe hands them over.
