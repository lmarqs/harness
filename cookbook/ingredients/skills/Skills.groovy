package thecook.cookbook.ingredients.skills

import thecook.annotations.Ingredient
import thecook.composition.ComponentId
import thecook.filesystem.TargetPath
import thecook.model.*

@Ingredient('skills')
class Skills implements Contributor {
  final ComponentId id
  final TargetPath directory
  private final Map<String, String> links = new LinkedHashMap<>()
  boolean adopt = false

  Skills(String name = 'skills', String directory = '.agents/skills') {
    id = ComponentId.of(name)
    this.directory = TargetPath.of(directory)
  }

  void link(String name, String source) {
    if (!(name ==~ /[a-z0-9][a-z0-9-]*/)) throw new IllegalArgumentException('Invalid skill name: ' + name)
    if (links.containsKey(name)) throw new IllegalArgumentException('Duplicate skill: ' + name)
    if (!source) throw new IllegalArgumentException('A skill link requires a source')
    links.put(name, source)
  }

  RuntimeFiles getFiles() {
    List<FileReference> references = links.keySet().collect { String name -> FileReference.file(directory.value + '/' + name) }
    new RuntimeFiles(new FileSet(references), FileSet.of())
  }

  List<OutputSpec> outputs() {
    List<OutputSpec> outputs = []
    links.each { String name, String source ->
      outputs.add(new SymbolicLinkOutput(id, TargetPath.of(directory.value + '/' + name), source, adopt))
    }
    outputs
  }
}
