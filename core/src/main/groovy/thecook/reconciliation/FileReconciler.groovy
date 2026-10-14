package thecook.reconciliation

import thecook.formats.Documents
import thecook.rendering.*

class FileReconciler {
  final Documents documents
  FileReconciler(Documents documents = new Documents()) {
    this.documents = documents
  }

  void validateRetained(Map snapshot, OwnershipRecord record) {
    if (record instanceof ExclusiveRecord) {
      if (!snapshot.exists || snapshot.content != record.rendered ||
          (record.kind == FileKind.SYMLINK && snapshot.kind != 'symlink'))
        throw new IllegalStateException('Edited managed ' + (record.kind == FileKind.SYMLINK ? 'symlink' : 'whole file'))
      return
    }
    def document = documents.open(record.kind.wireName, snapshot.content ?: '')
    document.seed(record.metadata)
    record.regions.each { identity, region ->
      if (document.read(identity.toString(), region.location) != region.rendered)
        throw new IllegalStateException("Edited managed region: ${identity}")
    }
  }

  FileResult reconcile(Map snapshot, OwnershipRecord before, RenderedFile desired, boolean eject, Set preserved = []) {
    def kind = desired?.kind ?: before.kind
    if (before && desired && (before.kind != desired.kind ||
        (before instanceof ExclusiveRecord && before.owner != ((ExclusiveFile) desired).owner)))
      throw new IllegalStateException('Output identity changed; unmount before transferring ownership')
    if (kind == FileKind.SYMLINK) return new SymbolicLinkReconciler().reconcile(snapshot, (ExclusiveRecord) before, (ExclusiveFile) desired, eject)
    if (kind == FileKind.WHOLE) return whole(snapshot, (ExclusiveRecord) before, (ExclusiveFile) desired, eject)
    partial(snapshot, (PartialRecord) before, (PartialFile) desired, kind, eject, preserved)
  }

  private FileResult partial(Map snapshot, PartialRecord before, PartialFile desired, FileKind kind, boolean eject, Set preserved) {
    def document = documents.open(kind.wireName, snapshot.content ?: '')
    document.seed(before?.metadata ?: [:])
    checkTransfers(before, desired)
    def pending = desired ? desired.regions.collectEntries { [(it.identity): it] } : [:]
    def identities = ((before?.regions?.keySet() ?: []) + pending.keySet()).unique()
    identities.sort { id -> (pending.containsKey(id) ? '1' : '0') + id.toString() }
    def nextRegions = [:]
    for (def identity : identities) {
      if (preserved.contains(identity)) {
        if (document.read(identity.toString(), before.regions[identity].location) != before.regions[identity].rendered)
          throw new IllegalStateException("Edited managed region: ${identity}")
        nextRegions[identity] = before.regions[identity]
        continue
      }
      def region = new RegionReconciler().reconcile(document, identity, before?.regions?.get(identity), pending[identity], eject)
      if (region) nextRegions[identity] = region
    }
    boolean created = before ? before.created : !snapshot.exists
    boolean originallyEmpty = before ? before.originallyEmpty : snapshot.exists && snapshot.content == ''
    def content = created && !desired && document.isEmpty() ? null : document.text
    if (!desired && originallyEmpty && document.isEmpty()) content = ''
    def state = (desired || nextRegions) ? new PartialRecord(kind, created, originallyEmpty, nextRegions, document.metadata) : null
    new FileResult(content, content == null ? null : (snapshot.mode ?: 'rw-r--r--'), state)
  }

  private static void checkTransfers(PartialRecord before, PartialFile desired) {
    if (!before || !desired || desired.kind == FileKind.BLOCKS) return
      for (RenderedRegion region : desired.regions) {
        def previous = before.regions.find { identity, old ->
          identity != region.identity &&
              ((KeyLocation) old.location).overlaps((KeyLocation) region.location)
        }
        if (previous) throw new IllegalStateException("Output identity changed: ${previous.key} conflicts with ${region.identity}; unmount before transferring ownership")
      }
  }

  private static FileResult whole(Map snapshot, ExclusiveRecord before, ExclusiveFile desired, boolean eject) {
    String next = desired?.content
    if (before && snapshot.content != before.rendered && (desired == null || snapshot.content != next))
      throw new IllegalStateException('Edited managed whole file')
    if (!before && snapshot.exists && !desired.adopt) {
      throw new IllegalStateException('Unmanaged whole file already exists; explicit adopt required')
    }
    if (eject) {
      return new FileResult(snapshot.content, snapshot.mode, null)
    }
    if (!desired) {
      return new FileResult(before.original.content, before.original.mode, null)
    }
    def mode = desired.mode ? Permissions.parse(desired.mode) : (snapshot.mode ?: 'rw-r--r--')
    new FileResult(next, mode, new ExclusiveRecord(FileKind.WHOLE, desired.owner, next,
        before ? before.original : snapshot, mode, desired.definitionIdentity ?: before?.definitionIdentity))
  }
}
