# TheCook repository setup

`setup.groovy` returns a `Batch` containing TheCook and Git. Git receives `TheCook.files.local`, which adds the local `.cook/` ownership state to its managed ignore block.

```sh
./cook recipes/the-cook/setup.groovy --dry-run
./cook recipes/the-cook/setup.groovy
```

This script reads no recipe preferences. Use [cookbook](../cookbook/README.md) for agent instructions, skill links, and permissions.
