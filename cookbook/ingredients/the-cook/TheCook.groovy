package thecook.cookbook.ingredients.thecook

import thecook.annotations.Ingredient
import thecook.composition.ComponentId
import thecook.model.*

@Ingredient('the-cook')
class TheCook implements Contributor {
  final ComponentId id
  // Ownership state is local, but must survive cleanup of disposable files.
  final RuntimeFiles files = new RuntimeFiles(
  FileSet.of(FileReference.directory('.cook')),
  FileSet.of())

  TheCook(String name = 'the-cook') {
    id = ComponentId.of(name)
  }

  List<OutputSpec> outputs() {
    []
  }
}
