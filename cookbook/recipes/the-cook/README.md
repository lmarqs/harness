# the-cook

Adds the-cook's local `.cook/` directory to the repository's `.gitignore`, so it stays out of version control.

## Usage

```sh
./cook the-cook
```

```groovy
book.recipe('the-cook', kitchen).mount()
```

## Arguments

None. The recipe ignores every option it receives.

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `.gitignore` | Ignore file | The `git/ignore` block, containing `/.cook/`. |

## Behaviour

- Lines outside the `git/ignore` block are left untouched.
- The recipe returns a component named `the-cook-setup` that includes the `the-cook` and `git` ingredients.
