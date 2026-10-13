# Add a Python dependency

Declare a requirement in a text manifest or in a TOML project dependency array, and generate a `dependency:python` task.

```groovy
book.recipe('add-python-dependency', kitchen, [
    dependency: 'example-library==1.2.3', manifest: 'requirements.txt',
    installer: 'pip', environment: '.venv/bin/python'
]).mount()
```

Required options are `dependency`, `manifest`, `installer`, and `environment` (the Python executable). Installer is `uv` or `pip`; the requirement must occupy one line.

A manifest ending in `.toml` uses `project.dependencies`, or `project.optional-dependencies.<group>` when `group` is supplied. Existing requirements for the same normalized package with different specifications are rejected. Other manifests receive a managed dependency block.

Optional options are `group` (TOML only), `id` (default `add-python-dependency`), `mise`, and `adopt`. The task installs the project in editable mode for TOML, or installs with `-r <manifest>` for a requirements file.

See [factory usage](../README.md).
