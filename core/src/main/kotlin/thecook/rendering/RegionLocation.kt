package thecook.rendering

sealed class RegionLocation

class BlockLocation : RegionLocation()

class KeyLocation(
  path: List<String>,
) : RegionLocation() {
  val path: List<String> = java.util.List.copyOf(path)

  init {
    require(this.path.isNotEmpty() && this.path.all { it.isNotEmpty() && '\n' !in it }) { "Invalid key path" }
  }

  fun overlaps(other: KeyLocation): Boolean =
    path.take(other.path.size) == other.path || other.path.take(path.size) == path
}
