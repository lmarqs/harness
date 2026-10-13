package thecook.reconciliation

import thecook.rendering.ExclusiveFile
import thecook.rendering.FileKind

class SymbolicLinkReconciler {
  FileResult reconcile(Map snapshot, ExclusiveRecord before, ExclusiveFile desired, boolean eject) {
    if (snapshot.exists && snapshot.kind != 'symlink') throw new IllegalStateException('Symlink output collides with a regular file')
    String next = desired?.content
    if (desired) StateSchema.linkTarget(next)
    if (before && (!snapshot.exists || snapshot.content != before.rendered)) throw new IllegalStateException('Edited managed symlink')
    if (!before && snapshot.exists && !desired.adopt) throw new IllegalStateException('Unmanaged symlink already exists; explicit adopt required')
    if (eject) return new FileResult(snapshot.content, null, null)
    if (!desired) return new FileResult(before.original.content, null, null)
    new FileResult(next, null, new ExclusiveRecord(FileKind.SYMLINK, desired.owner, next,
        before ? before.original : snapshot, null, desired.definitionIdentity ?: before?.definitionIdentity))
  }
}
