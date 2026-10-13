package thecook.cookbook.ingredients.git

import thecook.annotations.Ingredient
import thecook.composition.ComponentId
import thecook.filesystem.TargetPath
import thecook.model.*

@Ingredient('git')
class Git implements Contributor {
  final ComponentId id
  final FileInput ignore = new FileInput()
  final TargetPath path

  Git(String name = 'git', String path = '.gitignore') {
    this.id = ComponentId.of(name)
    this.path = TargetPath.of(path)
  }

  List<OutputSpec> outputs() {
    Provider<List<String>> content = {
      ignore.resolve().files.collect {
        pattern(it)
      }
    }
    [new BlockOutput(id, path, 'ignore', content)]
  }

  private static String pattern(FileReference file) {
    String path = file.path
    if (file.kind != FileKind.GLOB) {
      for (String special : ['*', '?', '[', ']']) path = path.replace(special, '\\' + special)
    }
    path = path.replace('#', '\\#').replace('!', '\\!').replace(' ', '\\ ')
    (file.scope == FileScope.ROOT ? '/' : '') + path + (file.kind == FileKind.DIRECTORY ? '/' : '')
  }
}
