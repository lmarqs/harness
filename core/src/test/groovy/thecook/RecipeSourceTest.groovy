package thecook

import org.junit.Test
import thecook.cookbook.RecipeSource
import thecook.model.RecipeParam

class RecipeSourceTest {
  private static final File COOKBOOK = new File(System.getProperty('cook.cookbook'))

  @Test void readsTextsAndDeclarationsWithoutResolvingImports() {
    def texts = RecipeSource.texts(new File(COOKBOOK, 'agents/instructions.groovy').text, 'instructions.groovy')
    assert texts.help.startsWith('Seeds `AGENTS.md` with a section template')
    assert texts.confirm == null
    assert texts.yieldText.startsWith('Have an agent fill in each section of `AGENTS.md`.')
    assert texts.params == [
      new RecipeParam(['harness'], 'String[]', 'Harnesses that read the shared instructions', '[]',
      ['claude', 'codex'], true)
    ]
    assert texts.ingredients == ['dev.thecook.agents.Agents', 'dev.thecook.agents.Claude', 'dev.thecook.agents.Codex']
  }

  @Test void keepsEscapedLineBreaksInTexts() {
    def texts = RecipeSource.texts(new File(COOKBOOK, 'github/aws-oidc.groovy').text, 'aws-oidc.groovy')
    assert texts.confirm.startsWith("The selected job's next run assumes the selected IAM role")
    assert texts.help.contains('--job=deploy \\\n')
  }

  @Test void recipeWithoutHelpHasNoTexts() {
    assert RecipeSource.texts('import a.B\nreturn { k, b, o -> }\n', 'plain.groovy') == null
    assert RecipeSource.texts('return { k, b, o -> }\n', 'plain.groovy') == null
  }
}
