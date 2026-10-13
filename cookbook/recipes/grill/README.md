# Grill task

Declare a mise task named `grill`.

```groovy
book.recipe('grill', kitchen, [script: 'git status']).mount()
```

`script` is required and becomes the task command. Optional options are `id` (default `grill`), `mise` (an existing mise component), and `adopt` (passed to mise). Applying the recipe does not run the command.

See [factory usage](../README.md).
