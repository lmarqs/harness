import thecook.cookbook.git.Git
import thecook.cookbook.thecook.TheCook
import thecook.model.Batch

def cook = new TheCook()
def git = new Git()
git.ignore.add(cook.files.local)

new Batch('the-cook')
    .include(cook)
    .include(git)
