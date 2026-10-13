import thecook.cookbook.ingredients.git.Git
import thecook.cookbook.ingredients.ralphtui.*
import thecook.cookbook.ingredients.agentpolicy.Policy
import thecook.cookbook.recipes.agentic.codex.CodexFiles
import thecook.cookbook.recipes.agentic.claude.ClaudeFiles
import thecook.model.Batch
def ralph = new Ralph(new RalphConfig(agent: 'codex'))
def git = new Git()
git.ignore.add(ralph.files.local)
def policy = new Policy([], [new Policy.Command(Policy.Decision.DENY, ['git', 'reset', '--hard'])])
new Batch('packaged').include(git).include(ralph)
    .include(new CodexFiles(policy)).include(new ClaudeFiles(policy))
