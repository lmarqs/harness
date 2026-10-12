package thecook.reconciliation

import thecook.formats.Documents

class FileReconciler {
    final Documents documents
    FileReconciler(Documents documents = new Documents()) { this.documents = documents }

    Map reconcile(Map snapshot, Map before, Map desired, boolean eject) {
        if (before && desired && (before.owner != desired.owner || before.kind != desired.kind))
            throw new IllegalStateException('Output identity changed; unmount before transferring ownership')
        def output = desired ?: before
        if (output.kind == 'symlink') return new SymbolicLinkReconciler().reconcile(snapshot, before, desired, eject)
        if (output.kind == 'whole') return whole(snapshot, before, desired, eject)
        def document = documents.open(output.kind, snapshot.content ?: '')
        document.seed(before?.metadata ?: [:])
        def nextRegions = [:]
        def identities = ((before?.regions?.keySet() ?: []) + (desired?.regions?.keySet() ?: [])).unique().sort()
        identities.each { key ->
            def id = output.owner + '/' + key
            def region = new RegionReconciler().reconcile(document, id, before?.regions?.get(key), desired?.regions?.get(key), eject)
            if (region) nextRegions[key] = region
        }
        def created = before ? before.created : !snapshot.exists
        def content = created && !desired && document.isEmpty() ? null : document.text
        def originallyEmpty = before ? before.originallyEmpty : snapshot.exists && snapshot.content == ''
        if (!desired && originallyEmpty && document.isEmpty()) content = ''
        def state = desired ? [owner: desired.owner, kind: desired.kind, created: created, originallyEmpty: originallyEmpty,
                              regions: nextRegions, metadata: document.metadata] : null
        [content: content, mode: content == null ? null : (snapshot.mode ?: 'rw-r--r--'), state: state]
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
                                        original: before ? before.original : snapshot, mode: mode]]
    }
}
