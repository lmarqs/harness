package thecook.reconciliation

import thecook.formats.Document

class RegionReconciler {
  Map reconcile(Document document, String identity, Map before, Map desired, boolean eject) {
    def definition = (before?.definition ?: desired ?: [:]).findAll { it.key != 'value' }
    def current = document.read(identity, definition)
    def next = desired == null ? null : document.render(desired.value)
    if (before && current != before.rendered && (desired == null || current != next))
      throw new IllegalStateException("Edited managed region: ${identity}")
    if (eject) {
      document.release(identity, definition); return null
    }
    if (desired == null) {
      restore(document, identity, definition, before); return null
    }
    if (!before && current != null && !desired.adopt)
      throw new IllegalStateException("Unmanaged region already exists: ${identity}; explicit adopt required")
    if (!before && desired.whenPresent && current == null) return null
    def original = before ? before.original : current
    definition += desired.findAll { it.key != 'value' }
    document.write(identity, definition, next)
    [rendered: document.read(identity, definition), original: original, definition: definition]
  }
  private static void restore(Document document, String id, Map definition, Map before) {
    if (before.original != null) {
      document.write(id, definition, before.original); return
    }
    document.remove(id, definition)
  }
}
