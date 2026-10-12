import thecook.cookbook.git.Git
import thecook.model.Batch
import thecook.model.FileReference
import thecook.model.FileSet

def git = new Git()

git.ignore.add(FileSet.of(FileReference.directory('.cook')))

new Batch('the-cook')
    .include(git)
