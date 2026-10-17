# Infra

Infra connects the [core](../core/README.md) to the places cookbook releases come from. It implements the core's
`CookbookRepository`, and the CLI calls it to build the repositories for a kitchen.

- `CookToml.repositories` reads the `repositories` list of the kitchen's `cook.toml`, or uses Maven Central when the
  kitchen has none. An entry is a Maven-layout repository, an `https://` URL or a path relative to the kitchen, or a
  git repository, `{ git = "<url>", dir = "<dir>" }`.
- `MavenRepository` reads an artifact's jar from a Maven-layout repository over `https://` or from a path.
- `GitRepository` reads an artifact's directory at its release tag. The machine's `git` fetches each tag, with the
  credentials git already has, into a cache that the CLI places in `$XDG_CACHE_HOME/the-cook/git`, or
  `~/.cache/the-cook/git` when that variable is unset.

`./gradlew check` runs `CookTomlTest`, which resolves from jars and git repositories built in the test.
