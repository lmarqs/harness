# add-python-dependency

Records one requirement in `pyproject.toml` or `requirements.txt` and declares a mise task that installs it.

```sh
./cook dev.thecook:add-python-dependency --dependency='httpx>=0.27' --group=dev
```

| Target | Part |
| --- | --- |
| `<dir>/pyproject.toml` | `project.dependencies`, or `project.optional-dependencies.<group>` |
| `<dir>/requirements.txt` | The section `dependencies` |
| `mise.toml` | `tasks."dependency:python"` |
