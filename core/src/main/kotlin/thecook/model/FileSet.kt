package thecook.model

import java.util.Collections

class FileSet(
  files: Collection<FileReference>,
) {
  val files: List<FileReference> = Collections.unmodifiableList(files.distinct())

  companion object {
    @JvmStatic fun of(vararg files: FileReference) = FileSet(files.toList())
  }
}

/** Locality and disposability describe paths; neither grants ownership. */
class RuntimeFiles(
  val local: FileSet,
  val ephemeral: FileSet,
)
