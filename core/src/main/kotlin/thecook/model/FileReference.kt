package thecook.model

import thecook.filesystem.requireRelativePath

enum class FileScope { ROOT, ANYWHERE }

enum class FileKind { FILE, DIRECTORY, GLOB }

data class FileReference(
  val path: String,
  val kind: FileKind,
  val scope: FileScope,
) {
  init {
    requireRelativePath(path)
  }

  companion object {
    @JvmStatic @JvmOverloads
    fun file(
      path: String,
      scope: FileScope = FileScope.ROOT,
    ) = FileReference(path, FileKind.FILE, scope)

    @JvmStatic @JvmOverloads
    fun directory(
      path: String,
      scope: FileScope = FileScope.ROOT,
    ) = FileReference(path, FileKind.DIRECTORY, scope)

    @JvmStatic @JvmOverloads
    fun glob(
      path: String,
      scope: FileScope = FileScope.ROOT,
    ) = FileReference(path, FileKind.GLOB, scope)
  }
}
