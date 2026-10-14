package thecook.reconciliation

import thecook.formats.Document
import thecook.composition.ContributionIdentity
import thecook.rendering.RenderedRegion

class RegionReconciler {
  RegionRecord reconcile(Document document, ContributionIdentity identity, RegionRecord before, RenderedRegion desired, boolean eject) {
    def location = before?.location ?: desired.location
    def current = document.read(identity.toString(), location)
    def next = desired == null ? null : document.render(desired.value)
    if (before && current != before.rendered && (desired == null || current != next))
      throw new IllegalStateException("Edited managed region: ${identity}")
    if (eject) {
      document.release(identity.toString(), location); return null
    }
    if (desired == null) {
      if (before.original != null) document.write(identity.toString(), location, before.original)
      else document.remove(identity.toString(), location)
      return null
    }
    if (!before && current != null && !desired.adopt)
      throw new IllegalStateException("Unmanaged region already exists: ${identity}; explicit adopt required")
    if (!before && desired.whenPresent && current == null) return null
    document.write(identity.toString(), desired.location, next)
    new RegionRecord(document.read(identity.toString(), desired.location), before ? before.original : current,
        desired.location, before ? before.adopt : desired.adopt, desired.whenPresent, desired.definitionIdentity ?: before?.definitionIdentity)
  }
}
