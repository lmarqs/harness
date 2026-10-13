package thecook.reconciliation

import thecook.composition.ComponentId
import thecook.composition.ContributionIdentity
import thecook.rendering.FileKind
import thecook.rendering.RegionLocation

sealed class OwnershipRecord(
  val kind: FileKind,
)

class ExclusiveRecord(
  kind: FileKind,
  val owner: ComponentId,
  val rendered: String,
  val original: Map<String, Any?>,
  val mode: String?,
  val definitionIdentity: String?,
) : OwnershipRecord(kind)

class RegionRecord(
  val rendered: String,
  val original: String?,
  val location: RegionLocation,
  val adopt: Boolean,
  val whenPresent: Boolean,
  val definitionIdentity: String?,
)

class PartialRecord(
  kind: FileKind,
  val created: Boolean,
  val originallyEmpty: Boolean,
  val regions: Map<ContributionIdentity, RegionRecord>,
  val metadata: Map<String, Any?>,
) : OwnershipRecord(kind)

class FileResult(
  val content: String?,
  val mode: String?,
  val state: OwnershipRecord?,
)
