import thecook.cookbook.ingredients.git.Git
import thecook.cookbook.ingredients.ralphtui.*
import thecook.model.Batch
def ralph = new Ralph(new RalphConfig(agent: preferences.agent ?: 'codex'))
def git = new Git()
git.ignore.add(ralph.files.local)
new Batch('personal-tools').include(git).include(ralph)
