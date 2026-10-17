package thecook.rendering

/** Where a recipe's region sits in a document: a marked block, or a key path. */
abstract class RegionLocation {
}

/** The block between a recipe's markers. */
final class BlockLocation extends RegionLocation {
}

/** A key path in a structured document. */
final class KeyLocation extends RegionLocation {
  final List<String> path

  KeyLocation(List<String> path) {
    this.path = List.copyOf(path)
    if (this.path.isEmpty() || this.path.any { it.isEmpty() || it.contains('\n') }) {
      throw new IllegalArgumentException('Invalid key path')
    }
  }

  boolean overlaps(KeyLocation other) {
    path.take(other.path.size()) == other.path || other.path.take(path.size()) == path
  }
}
