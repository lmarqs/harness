# update-docker-base

`update-docker-base` replaces the base image of one named stage in an existing Dockerfile and declares a
`docker:verify` mise task that builds that stage.

## Usage

```groovy
book.recipe('update-docker-base', kitchen, [
    adopt: true, path: 'Dockerfile', stage: 'build',
    expectedImage: 'node:20', image: 'node:22'
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `adopt` | yes | none | Boolean; must be `true`. |
| `path` | yes | none | Path of the existing Dockerfile. |
| `stage` | yes | none | Stage name after `AS`, matched exactly. |
| `expectedImage` | yes | none | Image the stage uses now. |
| `image` | yes | none | New image: a literal reference of letters, digits, `_`, `.`, `:`, `@`, `/` or `-`. |
| `id` | no | `update-docker-base` | Identity of the recipe component. |
| `mise` | no | a new `mise` ingredient | Existing mise ingredient component that receives the task. Pass one when the kitchen already holds a component named `mise`. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `<path>` | Text | The whole Dockerfile, with the stage's image replaced. |
| `mise.toml`, or the file of the supplied `mise` | TOML | The `tasks."docker:verify"` key: a task that runs `docker build -f '<path>' --target '<stage>' .`. |

## Inputs

The `docker:verify` task needs mise, Docker, a build context in the directory the task runs in, and access to the
image in a registry or the local cache.

## Behaviour

- A stage line has the form `FROM [--platform=<value>] <image> AS <name>`, with `FROM` and `AS` in any case. Exactly
  one stage line must name `stage`, and its image must be `expectedImage` or already `image`; otherwise the recipe
  fails. A missing Dockerfile, an `adopt` other than `true`, or an `image` outside the literal pattern also fails it.
- The recipe changes only the image on that line and keeps the rest of the line, including the `--platform` flag. It
  leaves other lines unchanged and writes the file with `\n` line endings and a final newline.
- The recipe takes over the Dockerfile and, when it creates the mise ingredient, an existing unmanaged task key.
- The recipe writes the task declaration. It does not run the task, pull the image or build it.

## Example

With the usage above and this `Dockerfile`:

```dockerfile
FROM --platform=$BUILDPLATFORM node:20 AS build
RUN npm ci
FROM nginx:stable AS runtime
```

the recipe writes:

```dockerfile
FROM --platform=$BUILDPLATFORM node:22 AS build
RUN npm ci
FROM nginx:stable AS runtime
```
