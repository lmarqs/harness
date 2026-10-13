package fixture

import groovy.transform.CompileStatic
import thecook.model.RecipeDeclaration
import thecook.model.RecipeInput

@CompileStatic
class Declaration {
  static RecipeDeclaration describe() {
    new RecipeDeclaration('declared', [
      new RecipeInput.RequiredPath('policy'),
      new RecipeInput.RequiredChoices('harness', ['codex', 'claude'])
    ])
  }
}
