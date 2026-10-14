# Add a Python dependency

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Declare a requirement in a text manifest or in a TOML project dependency array, and generate a `dependency:python` task.

```groovy
book.recipe('add-python-dependency', kitchen, [
    dependency: 'example-library==1.2.3', manifest: 'requirements.txt',
    installer: 'pip', environment: '.venv/bin/python'
]).mount()
```

Required options are `dependency`, `manifest`, `installer`, and `environment` (the Python executable). Installer is `uv` or `pip`; the requirement must occupy one line.

A manifest ending in `.toml` uses `project.dependencies`, or `project.optional-dependencies.<group>` when `group` is supplied. In the TOML case, existing requirements for the same normalized package with different specifications are rejected;
the text-manifest case writes a block without checking other requirements outside it. The TOML array is managed as
one key, so an existing unmanaged array requires adoption even when the new requirement is simply appended. Other manifests receive a managed dependency block.

Optional options are `group` (TOML only), `id` (default `add-python-dependency`), `mise`, and `adopt`. The task installs the project in editable mode for TOML, or installs with `-r <manifest>` for a requirements file when invoked separately. Cooking does not create the Python
environment or install packages. Running the task requires mise, an existing Python environment with pip, or uv
for the uv path, and a usable requirements file or installable project. A TOML dependency stub alone is not a
complete installable project. Package sources must be reachable or cached. The text block does not use `adopt`;
that option applies to the TOML key and mise supplier, and managed edits still fail reconciliation.

See [factory usage](../README.md).
