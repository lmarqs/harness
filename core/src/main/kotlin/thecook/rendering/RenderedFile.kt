package thecook.rendering

import thecook.annotations.Ingredient
import thecook.composition.ComponentId
import thecook.composition.ContributionIdentity
import thecook.filesystem.TargetPath
import thecook.model.BlockOutput
import thecook.model.Contributor
import thecook.model.FileOutput
import thecook.model.OutputSpec
import thecook.model.StructuredOutput
import thecook.model.SymbolicLinkOutput

enum class FileKind {
  WHOLE,
  SYMLINK,
  BLOCKS,
  JSON,
  TOML,
  YAML,
  ;

  val wireName: String get() = name.lowercase()
  val exclusive: Boolean get() = this == WHOLE || this == SYMLINK

  companion object {
    @JvmStatic fun parse(value: String): FileKind =
      entries.find { it.wireName == value }
        ?: throw IllegalArgumentException("Unsupported output format: $value")
  }
}

sealed class RegionLocation

class BlockLocation : RegionLocation()

class KeyLocation(
  path: List<String>,
) : RegionLocation() {
  val path: List<String> = java.util.List.copyOf(path)

  init {
    require(this.path.isNotEmpty() && this.path.all { it.isNotEmpty() && '\n' !in it }) { "Invalid key path" }
  }

  fun identity(owner: ComponentId): ContributionIdentity =
    ContributionIdentity(
      owner,
      path.joinToString("/") { it.replace("~", "~0").replace("/", "~1") },
    )

  fun overlaps(other: KeyLocation): Boolean =
    path.take(other.path.size) == other.path || other.path.take(path.size) == path
}

class RenderedRegion
  @JvmOverloads
  constructor(
    val identity: ContributionIdentity,
    val location: RegionLocation,
    val value: Any?,
    val adopt: Boolean = false,
    val whenPresent: Boolean = false,
    val definitionIdentity: String? = null,
  )

sealed class RenderedFile(
  val path: TargetPath,
  val kind: FileKind,
) {
  companion object {
    @JvmStatic fun evaluate(contributors: List<Contributor>): List<RenderedFile> {
      val declarations =
        contributors.flatMap { contributor ->
          val provenance = contributor.javaClass.getAnnotation(Ingredient::class.java)?.value
          contributor.outputs().map { spec ->
            require(spec.owner == contributor.id) { "Output owner differs from its contributor" }
            spec to provenance
          }
        }
      return declarations.map { (spec, provenance) -> evaluate(spec, provenance) }
    }

    @JvmStatic fun evaluate(
      spec: OutputSpec,
      provenance: String?,
    ): RenderedFile =
      when (spec) {
        is BlockOutput -> {
          require(
            spec.region.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_.:-]*")),
          ) { "Invalid region identity: ${spec.region}" }
          PartialFile(
            spec.path,
            FileKind.BLOCKS,
            listOf(
              RenderedRegion(
                ContributionIdentity(spec.owner, spec.region),
                BlockLocation(),
                spec.content.get(),
                definitionIdentity = provenance,
              ),
            ),
          )
        }

        is StructuredOutput -> {
          PartialFile(
            spec.path,
            FileKind.parse(spec.format.name.lowercase()),
            spec.keys.map {
              val location = KeyLocation(it.path)
              RenderedRegion(
                location.identity(spec.owner),
                location,
                it.value.get(),
                it.adopt,
                it.whenPresent,
                provenance,
              )
            },
          )
        }

        is FileOutput -> {
          ExclusiveFile(
            spec.path,
            FileKind.WHOLE,
            spec.owner,
            spec.content.get(),
            if (spec.executable) "0755" else null,
            spec.adopt,
            provenance,
          )
        }

        is SymbolicLinkOutput -> {
          ExclusiveFile(spec.path, FileKind.SYMLINK, spec.owner, spec.target, null, spec.adopt, provenance)
        }
      }
  }
}

class ExclusiveFile(
  path: TargetPath,
  kind: FileKind,
  val owner: ComponentId,
  val content: String,
  val mode: String?,
  val adopt: Boolean,
  val definitionIdentity: String?,
) : RenderedFile(path, kind) {
  init {
    require(kind.exclusive)
  }
}

class PartialFile(
  path: TargetPath,
  kind: FileKind,
  regions: List<RenderedRegion>,
) : RenderedFile(path, kind) {
  val regions: List<RenderedRegion> = java.util.List.copyOf(regions)

  init {
    require(!kind.exclusive)
    require(
      regions.all {
        (it.location is BlockLocation) == (kind == FileKind.BLOCKS)
      },
    ) { "Region location differs from file format" }
  }
}
