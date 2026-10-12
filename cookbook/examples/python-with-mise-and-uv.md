# Python, mise, uv, and `.venv`

This is a **concept sketch**, not a working CLI or supported schema. It models the arrangement found in `../medprev-rest-api`, not every kind of uv project.

That repository has `python = "3.13"` and `_.python.venv = { path = ".venv", create = true }` in `mise.toml`. Its `.mise/tasks/python/setup` installs `requirements.txt` with `uv pip install` when uv is available, falling back to `python3 -m pip`. It ignores `/.venv/`. It has no `pyproject.toml` or `uv.lock` at the root. In this arrangement, **mise configures and activates the environment; uv is an installer used by a task**.

```sh
the-cook init
./cook ingredient add git python@3.13 uv-pip
./cook kitchen apply
```

`python@3.13` selects the interpreter. This cookbook's [Python ingredient](../ingredients/python.yaml) defaults to `venv: mise`, so it also contributes `_.python.venv` and the `/.venv/` ignore rule. It requires [mise](../ingredients/mise.yaml), selected automatically as the sole owner of `mise.toml`'s existence. [uv-pip](../ingredients/uv-pip.yaml) contributes the setup task that reads the team's existing `requirements.txt`; it does not create the environment or own the requirements.

The relevant result is:

```toml
# mise.toml
[tools]
python = "3.13"

[env]
_.python.venv = { path = ".venv", create = true }
```

`node@22` can be selected alongside this Python setup. Both runtimes then contribute keys to the same `mise.toml`, while only the mise ingredient has `owns: true` for that file. A Go ingredient could contribute `tools.go` the same way; there is no `mise-node` or `mise-go` ingredient. The `owns` flag for `mise.toml` does not mean Cook owns the generated `.venv` directory.

## Three distinct roles

| Choice | What it decides | What it does not decide |
| --- | --- | --- |
| `python@3.13` | Interpreter version and this cookbook's `venv: mise` choice | Which dependencies go into `.venv` |
| `uv-pip` | Task installs from `requirements.txt`, using uv when present | Ownership of `.venv` or a `uv.lock` |

An actual **uv project** is a different mode: `pyproject.toml` declares dependencies, `uv.lock` records resolved versions, and `uv sync` manages `.venv`. That setup would select `venv: none` on Python and let a uv-project ingredient configure mise to source uv's environment; it must not also declare `_.python.venv` as the creator. Calling both modes simply `uv` would hide who owns the environment. The cookbook still needs a concrete switch design between the two workflows.

The reference already has a team-written `.mise/tasks/python/setup`. Adding the `uv-pip` ingredient to that checkout needs an explicit adoption or conflict path; Cook cannot overwrite the existing task merely because the ingredient declares `owns: true`. The reference also does not list uv in `mise.toml`, so the task's uv-or-pip branch depends on what is installed on the machine. Selecting `uv-pip` alone cannot guarantee that uv runs. Mise may create `.venv` with uv, which does not seed pip by default; if uv later disappears, the fallback can fail. A cookbook that promises repeatable setup must select and install uv or explicitly seed pip.

## What a version switch exposes

```sh
./cook ingredient use python@3.14
./cook kitchen apply
```

Changing `tools.python` is only the file edit. An existing `.venv` may still use Python 3.13. A complete apply must detect that mismatch, rebuild the environment only when it can establish that it is generated and disposable, and rerun the selected dependency setup. It must report a failure rather than claim Python 3.14 is ready. Because this reference uses `pyyaml>=6` in `requirements.txt` with `uv pip install`, it has no locked dependency resolution; the two-command switch cannot promise identical packages across machines.

This example tests the boundary of `files`: it can express the committed configuration and task, but not the lifecycle of a generated directory by itself. A station can bundle the first selection, and a recipe can run a session, but neither replaces the environment and installer behavior that the selected ingredients must declare.
