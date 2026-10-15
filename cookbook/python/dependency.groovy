/**
 * Records one requirement in `pyproject.toml` or `requirements.txt` and declares a mise task that installs it.
 *
 * ## Before applying
 *
 * Choose the project directory and manifest. For `pyproject.toml`, the recipe reads the selected dependency
 * group and rejects a different specification for the same package already present there. mise, the selected
 * installer and the project's `.venv` virtual environment must be available to run the install task.
 * The `pyproject.toml` project must support editable installation when that manifest is selected.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:python:0.1.0:dependency --dependency='httpx>=0.27' --group=dev
 * ./cook dev.thecook:python:0.1.0:dependency --dependency='httpx>=0.27' --manifest=requirements.txt --installer=pip --dir=services/api
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `<dir>/pyproject.toml` | The package's entry in `project.dependencies`, matched by package name | `--manifest=pyproject.toml` without a group |
 * | `<dir>/pyproject.toml` | The package's entry in `project.optional-dependencies.<group>`, matched by package name | `--manifest=pyproject.toml` with a group |
 * | `<dir>/requirements.txt` | The section `dependencies` | `--manifest=requirements.txt` |
 * | `mise.toml` | `tasks."dependency:python"`, owned whole, running the selected installer in `<dir>` | Always |
 *
 * ## After applying
 *
 * Run `mise run dependency:python` to install into the project's `.venv`. The task installs the requirements
 * file or the editable project with the selected optional group. `--group` has no effect for `requirements.txt`.
 *
 * ## With other recipes
 *
 * `dev.thecook:mise:0.1.0:runtime-version --runtime=python` pins the interpreter at the destination root;
 * use `--dir=.` here for that project. Each application of this dependency recipe declares the same
 * `tasks."dependency:python"` key, so the last recipe in an invocation selects its installer and directory.
 * Distinct pyproject dependency entries compose; requirements-file applications share the whole section.
 */
import dev.thecook.python.Python
import dev.thecook.mise.Mise

@param(value = ['dependency'], doc = 'One requirement, such as httpx>=0.27') String dependency
@param(value = ['group'], default = null, doc = 'Optional dependency group') String group
@param(value = ['installer'], choices = ['uv', 'pip'], default = 'uv', doc = 'The installer the mise task runs') String installer
@param(value = ['manifest'], choices = ['pyproject.toml', 'requirements.txt'], default = 'pyproject.toml', doc = 'The manifest that records the requirement') String manifest
@param(value = ['dir'], default = '.', doc = 'The project directory') String dir

@ingredient(Python) def python
@ingredient(Mise) def mise

python.directory = dir
python.installer = installer

if (manifest == 'pyproject.toml') python.dependency(dependency, group ?: '')
else python.requirement(dependency)

mise.task('dependency:python', python.installCommand())
