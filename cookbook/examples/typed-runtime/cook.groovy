import thecook.cookbook.git.Git
import thecook.cookbook.ralph.Ralph
import thecook.cookbook.ralph.RalphConfig
import thecook.model.Batch
import thecook.model.FileReference
import thecook.model.FileSet

// A repository script is intentionally dynamic; it consumes compiled contracts.
def config = new RalphConfig()
config.agent = preferences.agent ?: 'codex'
def ralph = new Ralph(config)
def git = new Git()
git.ignore.add(ralph.files.local)
git.ignore.add(ralph.files.ephemeral)
def excluded = preferences.excluded instanceof String ? preferences.excluded.split(',') : preferences.excluded ?: []
for (def path : excluded) {
    git.ignore.add(FileSet.of(FileReference.file(path.toString())))
}
def batch = new Batch('agentic-runtime').include(git)
if (preferences.enabled?.toString() != 'false') batch.include(ralph)
batch
