# Recipe README template

Every recipe directory has a `README.md` that explains that recipe and nothing else. Its reader already knows the core
and the architecture: how recipes are invoked and composed, how changes are reviewed and approved, and how ownership,
the lock and batches work.

## Rules

- Describe this recipe only: what it writes, what it reads, what it accepts and what it rejects.
- Do not explain or link the CLI, the core, the architecture, ADRs or other documentation outside `cookbook/`.
- Describe what the recipe does now, in the present tense. No plans, history or comparison with another design.
- Name another recipe only when this recipe composes it or is composed by it, and link that recipe's README.
- Keep the section order below. Omit an optional section rather than leave it empty.
- Wrap prose at 120 characters.

## Template

````markdown
# <recipe name as invoked, for example agentic/permission>

<One or two present-tense sentences: what the recipe writes into a repository and what the developer gets from it.>

## Usage

```sh
./cook <recipe> --<argument>=<value>
```

<Show the command line when every argument can be given as a string. Show the composition call when the recipe is
meant to be composed or an argument needs a Groovy map, list, boolean or component.>

```groovy
book.recipe('<recipe>', kitchen, [<argument>: '<value>']).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `<argument>` | yes | none | <What the value selects; its accepted values and type.> |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `<path>` | <JSON, TOML, Markdown, ignore file, symbolic link, …> | <The keys, the named section or the whole file.> |

## Inputs

<Optional. Files, tools and services the recipe reads, or that its output needs in order to run, and where each one
must be.>

## Behaviour

<Optional. Rules specific to this recipe: what it validates and rejects, what it leaves untouched, which recipes it
composes or exposes to a composing recipe.>

## Example

<Optional. A minimal input and the output excerpt it produces.>

## Development

<Optional, for a recipe with its own Gradle module.>

```sh
./gradlew -p cookbook/recipes/<recipe> check
```
````
