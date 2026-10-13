package thecook.reconciliation

import thecook.filesystem.Repository
import thecook.rendering.RenderTree
import thecook.rendering.FileKind

class Reconciler {
  final FileReconciler files
  Reconciler(FileReconciler files = new FileReconciler()) {
    this.files = files
  }

  ChangePlan plan(Repository repository, RenderTree tree, boolean eject = false) {
    def stateSnapshot = repository.snapshot(repository.metadata('state.json'))
    def state = repository.state(stateSnapshot)
    def owned = OwnershipCodec.decode(state)
    def desired = tree.outputs.collectEntries { [(it.path.value): it] }
    def previousApplications = eject ? [:] : (state.applications ?: [:])
    def selected = tree.applications.keySet()
    def nextApplications = previousApplications.findAll { id, paths -> !selected.contains(id) }
    tree.applications.each { id, files ->
      nextApplications[id] = files.collectEntries { file ->
        [(file.path.value): file.kind.exclusive ? ['*'] : ((thecook.rendering.PartialFile) file).regions.collect { it.identity.toString() }]
      }
    }
    def previousSelected = previousApplications.findAll { id, paths -> selected.contains(id) }
    def paths = (owned.keySet() + desired.keySet()).unique().sort()
    def observed = [:]
    def changes = []
    def nextState = [version: 2, files: [:]]
    paths.each { path ->
      boolean affected = selected.isEmpty() || desired.containsKey(path) || previousSelected.values().any { it.containsKey(path) }
      if (selected.isEmpty() && !desired.containsKey(path) &&
          previousApplications.values().any { it.containsKey(path) } &&
          owned[path] instanceof ExclusiveRecord) affected = false
      if (!affected) {
        observed[path] = repository.snapshot(repository.target(path, owned[path].kind == FileKind.SYMLINK),
            owned[path].kind == FileKind.SYMLINK)
        nextState.files[path] = OwnershipCodec.encode(owned[path])
        return
      }
      boolean link = desired[path]?.kind == FileKind.SYMLINK || owned[path]?.kind == FileKind.SYMLINK
      def snapshot = repository.snapshot(repository.target(path, link), link)
      observed[path] = snapshot
      def result
      try {
        def withdrawn = previousSelected.values().collectMany { it[path] ?: [] }.toSet()
        def requested = desired[path]?.kind?.exclusive ? ['*'].toSet() :
          (desired[path] instanceof thecook.rendering.PartialFile ? desired[path].regions.collect { it.identity.toString() }.toSet() : [] as Set)
        def other = previousApplications.findAll { id, membership -> !selected.contains(id) }
            .values().collectMany { it[path] ?: [] }.toSet()
        if (requested.intersect(other)) throw new IllegalArgumentException("Application contribution already selected elsewhere: ${path}")
        def preserved = [] as Set
        if (owned[path] instanceof PartialRecord) {
          preserved = owned[path].regions.keySet().findAll {
            (selected ? !withdrawn.contains(it.toString()) : other.contains(it.toString())) &&
                !requested.contains(it.toString())
          }.toSet()
        }
        def target = desired[path]
        if (preserved && target == null)
          target = new thecook.rendering.PartialFile(thecook.filesystem.TargetPath.of(path), owned[path].kind, [])
        result = files.reconcile(snapshot, owned[path], target, eject, preserved)
      }
      catch (IllegalStateException error) {
        throw new IllegalStateException(path + ': ' + error.message, error)
      }
      catch (IllegalArgumentException error) {
        throw new IllegalArgumentException(path + ': ' + error.message, error)
      }
      if (snapshot.content != result.content || snapshot.mode != result.mode) {
        def change = [path: path, before: snapshot, content: result.content, mode: result.mode]
        if (link) change.kind = 'symlink'
        changes.add(change)
      }
      if (result.state) nextState.files[path] = OwnershipCodec.encode(result.state)
    }
    if (nextApplications) nextState.applications = nextApplications
    new ChangePlan(repository, [version: 1, root: repository.directory.path, observed: observed,
      changes: changes, stateBefore: stateSnapshot, stateAfter: nextState])
  }
}
