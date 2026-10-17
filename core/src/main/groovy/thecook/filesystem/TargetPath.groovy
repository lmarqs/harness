package thecook.filesystem

import groovy.transform.EqualsAndHashCode

/** A path a recipe writes: relative, inside the destination, and outside `.cook` and `.git`. */
@EqualsAndHashCode
final class TargetPath {
  final String value

  TargetPath(String value) {
    def parts = value.split('/', -1)
    if (value.isEmpty() || value.contains('\\') || value.contains(':') || value.chars().anyMatch { it < 32 || it == 127 } ||
        parts.any { it.isEmpty() || it == '.' || it == '..' } || parts[0].toLowerCase(Locale.ROOT) in ['.cook', '.git']) {
      throw new IllegalArgumentException("Unsafe output path: ${value}")
    }
    this.value = value
  }

  static TargetPath of(String value) {
    new TargetPath(value)
  }

  @Override String toString() {
    value
  }
}
