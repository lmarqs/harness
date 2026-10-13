package thecook.cookbook.recipes

import thecook.annotations.Recipe
import thecook.cookbook.ingredients.git.Git
import thecook.cookbook.ingredients.ralphtui.Ralph
import thecook.cookbook.ingredients.ralphtui.RalphConfig
import thecook.model.*

@Recipe('agentic-runtime')
class AgenticRuntime implements RecipeDefinition {
  final RalphConfig config
  final boolean enabled

  AgenticRuntime(RalphConfig config, boolean enabled = true) {
    this.config = config; this.enabled = enabled
  }

  Batch compose() {
    Git git = new Git()
    Ralph ralph = new Ralph(config)
    // Keep personal runtime data excluded even while Ralph is disabled.
    git.ignore.add(ralph.files.local)
    git.ignore.add(ralph.files.ephemeral)
    Batch batch = new Batch('agentic-runtime').include(git)
    if (enabled) batch.include(ralph)
    batch
  }
}
