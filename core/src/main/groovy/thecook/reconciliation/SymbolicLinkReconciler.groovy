package thecook.reconciliation

class SymbolicLinkReconciler {
    Map reconcile(Map snapshot, Map before, Map desired, boolean eject) {
        if (snapshot.exists && snapshot.kind != 'symlink')
            throw new IllegalStateException('Symlink output collides with a regular file')
        String next = desired ? desired.regions.content.value?.toString() : null
        if (desired) StateSchema.linkTarget(next)
        if (before && (!snapshot.exists || snapshot.content != before.rendered))
            throw new IllegalStateException('Edited managed symlink')
        if (!before && snapshot.exists && !desired.adopt)
            throw new IllegalStateException('Unmanaged symlink already exists; explicit adopt required')
        if (eject) return [content: snapshot.content, mode: null, kind: 'symlink', state: null]
        if (!desired) return [content: before.original.content, mode: null, kind: 'symlink', state: null]
        [content: next, mode: null, kind: 'symlink',
         state: [owner: desired.owner, kind: 'symlink', rendered: next, original: before ? before.original : snapshot]]
    }
}
