# add-python-dependency

`add-python-dependency` declares one requirement in a requirements file or in the dependency array of a TOML project
file, and a `dependency:python` mise task that installs it with pip or uv.

## Usage

```groovy
book.recipe('add-python-dependency', kitchen, [
    dependency: 'example-library==1.2.3', manifest: 'requirements.txt',
    installer: 'pip', environment: '.venv/bin/python'
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `dependency` | yes | none | One requirement on a single line, for example `example-library==1.2.3`. In a TOML manifest it must begin with a package name. |
| `manifest` | yes | none | Path of the manifest. A path ending in `.toml` selects the TOML project array; any other path is a requirements file. |
| `installer` | yes | none | `pip` or `uv`. |
| `environment` | yes | none | Path of the Python executable the task installs into, for example `.venv/bin/python`. |
| `group` | no | none | TOML manifests only: selects `project.optional-dependencies.<group>` instead of `project.dependencies`. |
| `id` | no | `add-python-dependency` | Identity of the recipe component. It names the requirements-file block. |
| `mise` | no | a new `mise` ingredient | Existing mise ingredient component that receives the task. Pass one when the kitchen already holds a component named `mise`. |
| `adopt` | no | `false` | Boolean. `true` takes over an existing unmanaged TOML dependency array and, when the recipe creates the mise ingredient, an existing unmanaged task key. The requirements-file block does not use it. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `<manifest>` ending in `.toml` | TOML | The `project.dependencies` array, or `project.optional-dependencies.<group>`: its existing entries plus `<dependency>`. |
| any other `<manifest>` | Text with marker blocks | The block between `# cook:begin <id>/dependency` and `# cook:end <id>/dependency`, holding `<dependency>`. |
| `mise.toml`, or the file of the supplied `mise` | TOML | The `tasks."dependency:python"` key. |

The task runs `'<environment>' -m pip install` with pip, or `uv pip install --python '<environment>'` with uv. For a
TOML manifest the command ends with `-e '<directory>'`, where `<directory>` is the manifest's parent directory or `.`;
with `group`, the editable target is `<directory>[<group>]`. For a requirements file it ends with `-r '<manifest>'`.

## Inputs

- For a TOML manifest, the recipe reads the existing file, when there is one, to keep the array's current entries.
- The task needs mise and the Python executable at `environment`. With pip, that executable must provide the `pip`
  module; with uv, `uv` must be available to the task.
- For a TOML manifest, the manifest's directory must hold an installable project. The recipe writes only the
  dependency array.
- Package indexes must be reachable, or the packages cached.

## Behaviour

- TOML manifest: a file that does not parse as TOML fails the recipe. A requirement whose package name, compared in
  lower case with runs of `-`, `_` and `.` read as one `-`, already appears in the array with a different
  specification fails the recipe. An identical existing entry stays once. The whole array is one key, so an existing
  unmanaged array needs `adopt: true` even when the recipe only appends to it.
- Requirements file: the recipe does not check the rest of the file, so a requirement for the same package outside the
  block stays.
- An `installer` other than `pip` or `uv`, or a `dependency` that contains a line break, fails the recipe.
- The recipe writes the task declaration. It does not run the task, create the Python environment or install packages.

## Example

With the usage above and this `requirements.txt`:

```text
requests==2.32.3
```

the recipe writes:

```text
requests==2.32.3
# cook:begin add-python-dependency/dependency
example-library==1.2.3
# cook:end add-python-dependency/dependency
```
