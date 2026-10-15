@param(['dependency'], doc = 'One requirement, such as httpx>=0.27') String dependency
@param(['group'], default = null, doc = 'Optional dependency group') String group
@param(['installer'], choices = ['uv', 'pip'], default = 'uv') String installer
@param(['manifest'], choices = ['pyproject.toml', 'requirements.txt'], default = 'pyproject.toml') String manifest
@param(['dir'], default = '.', doc = 'The project directory') String dir

@ingredient(Python) def python
@ingredient(Mise) def mise

python.directory = dir
python.installer = installer

if (manifest == 'pyproject.toml') python.dependency(dependency, group ?: '')
else python.requirement(dependency)

mise.task('dependency:python', python.installCommand())
