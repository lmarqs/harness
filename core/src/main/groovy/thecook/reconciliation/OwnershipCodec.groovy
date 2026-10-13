package thecook.reconciliation

import thecook.composition.ComponentId
import thecook.composition.ContributionIdentity
import thecook.rendering.*

/** The persistence seam: runtime ownership records never carry wire-shaped region maps. */
class OwnershipCodec {
  static Map<String, OwnershipRecord> decode(Map state) {
    StateSchema.current(state).files.collectEntries { path, file ->
      def kind = FileKind.parse(file.kind)
      if (kind.exclusive) return [(path): new ExclusiveRecord(kind, ComponentId.of(file.owner), file.rendered,
          file.original, file.mode, file.definitionIdentity)]
      def regions = file.regions.collectEntries { address, region ->
        def location = kind == FileKind.BLOCKS ? new BlockLocation() : new KeyLocation(region.definition.path)
        [(ContributionIdentity.parse(address)): new RegionRecord(region.rendered, region.original, location,
          region.definition.adopt ?: false, region.definition.whenPresent ?: false, region.definitionIdentity)]
      }
      def metadata = file.metadata
      if (kind == FileKind.BLOCKS) {
        def legacy = file.regions.findAll { it.value.definition.separator }.keySet()
        metadata = metadata + [separators: ((metadata.separators ?: []) + legacy).unique().sort()]
      }
      [(path): new PartialRecord(kind, file.created, file.originallyEmpty ?: false, regions, metadata)]
    }
  }

  static Map encode(OwnershipRecord record) {
    if (record instanceof ExclusiveRecord) return [kind: record.kind.wireName, owner: record.owner.value,
        rendered: record.rendered, original: record.original, mode: record.mode, definitionIdentity: record.definitionIdentity]
    PartialRecord partial = (PartialRecord) record
    def regions = partial.regions.collectEntries { identity, region ->
      def definition = [adopt: region.adopt, whenPresent: region.whenPresent]
      if (region.location instanceof KeyLocation) definition.path = region.location.path
      [(identity.toString()): [owner: identity.instance.value, rendered: region.rendered, original: region.original,
          definition: definition, definitionIdentity: region.definitionIdentity]]
    }
    [kind: partial.kind.wireName, created: partial.created, originallyEmpty: partial.originallyEmpty,
      regions: regions, metadata: partial.metadata]
  }
}
