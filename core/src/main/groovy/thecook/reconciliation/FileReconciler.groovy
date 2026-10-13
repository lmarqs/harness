package thecook.reconciliation

import thecook.formats.Documents

class FileReconciler {
  final Documents documents
  FileReconciler(Documents documents = new Documents()) {
    this.documents = documents
  }

  Map reconcile(Map snapshot, Map before, Map desired, boolean eject) {
    if (before && desired && (before.kind != desired.kind || (before.kind in ['whole', 'symlink'] && before.owner != desired.owner)))
      throw new IllegalStateException('Output identity changed; unmount before transferring ownership')
    def output = desired ?: before
    if (output.kind == 'symlink') return new SymbolicLinkReconciler().reconcile(snapshot, before, desired, eject)
    if (output.kind == 'whole') return whole(snapshot, before, desired, eject)
    def document = documents.open(output.kind, snapshot.content ?: '')
    document.seed(before?.metadata ?: [:])
    checkTransfers(before, desired)
    def nextRegions = [:]
    def identities = ((before?.regions?.keySet() ?: []) + (desired?.regions?.keySet() ?: [])).unique()
    // Remove withdrawn regions first; the remaining declarations then see the restored document.
    identities.sort { id -> (desired?.regions?.containsKey(id) ? '1' : '0') + id }
    identities.each { id ->
      def previous = before?.regions?.get(id)
      def next = desired?.regions?.get(id)
      def definition = next?.findAll { !(it.key in ['owner', 'definitionIdentity']) }
      def region = new RegionReconciler().reconcile(document, id, previous, definition, eject)
      if (region) nextRegions[id] = region + [owner: next.owner, definitionIdentity: next.definitionIdentity ?: previous?.definitionIdentity]
    }
    def created = before ? before.created : !snapshot.exists
    def content = created && !desired && document.isEmpty() ? null : document.text
    def originallyEmpty = before ? before.originallyEmpty : snapshot.exists && snapshot.content == ''
    if (!desired && originallyEmpty && document.isEmpty()) content = ''
    def state = desired ? [kind: desired.kind, created: created, originallyEmpty: originallyEmpty,
      regions: nextRegions, metadata: document.metadata] : null
    [content: content, mode: content == null ? null : (snapshot.mode ?: 'rw-r--r--'), state: state]
  }
  private static void checkTransfers(Map before, Map desired) {
    if (!before || !desired || desired.kind == 'blocks') return
      desired.regions.each { address, region ->
        def previous = before.regions.find { oldAddress, old ->
          oldAddress != address && (old.definition.path.take(region.path.size()) == region.path ||
              region.path.take(old.definition.path.size()) == old.definition.path)
        }
        if (previous)
          throw new IllegalStateException("Output identity changed: ${previous.key} conflicts with ${address}; unmount before transferring ownership")
      }
  }
  private static Map whole(Map snapshot, Map before, Map desired, boolean eject) {
    def next = desired ? desired.regions.content.value.toString() : null
    if (before && snapshot.content != before.rendered && (desired == null || snapshot.content != next))
      throw new IllegalStateException('Edited managed whole file')
    if (!before && snapshot.exists && !desired.adopt)
      throw new IllegalStateException('Unmanaged whole file already exists; explicit adopt required')
    if (eject) return [content: snapshot.content, mode: snapshot.mode, state: null]
    if (!desired) return [content: before.original?.content, mode: before.original?.mode, state: null]
    def mode = desired.mode ? Permissions.parse(desired.mode) : (snapshot.mode ?: 'rw-r--r--')
    [content: next, mode: mode, state: [owner: desired.owner, kind: 'whole', rendered: next,
        original: before ? before.original : snapshot, mode: mode, definitionIdentity: desired.definitionIdentity ?: before?.definitionIdentity]]
  }
}
