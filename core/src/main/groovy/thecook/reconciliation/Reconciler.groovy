package thecook.reconciliation

import thecook.filesystem.Repository
import thecook.rendering.RenderTree

class Reconciler {
    final FileReconciler files
    Reconciler(FileReconciler files = new FileReconciler()) { this.files = files }

    ChangePlan plan(Repository repository, RenderTree tree, boolean eject = false) {
        def stateSnapshot = repository.snapshot(repository.metadata('state.json'))
        def state = repository.state(stateSnapshot)
        def desired = tree.outputs.collectEntries { [(it.path): it] }
        def paths = (state.files.keySet() + desired.keySet()).unique().sort()
        def observed = [:]
        def changes = []
        def nextState = [version: 1, files: [:]]
        paths.each { path ->
            boolean link = desired[path]?.kind == 'symlink' || state.files[path]?.kind == 'symlink'
            def snapshot = repository.snapshot(repository.target(path, link), link)
            observed[path] = snapshot
            def result = files.reconcile(snapshot, state.files[path], desired[path], eject)
            if (snapshot.content != result.content || snapshot.mode != result.mode) {
                def change = [path: path, before: snapshot, content: result.content, mode: result.mode]
                if (link) change.kind = 'symlink'
                changes.add(change)
            }
            if (result.state) nextState.files[path] = result.state
        }
        new ChangePlan(repository, [version: 1, root: repository.directory.path, observed: observed,
                                   changes: changes, stateBefore: stateSnapshot, stateAfter: nextState])
    }
}
