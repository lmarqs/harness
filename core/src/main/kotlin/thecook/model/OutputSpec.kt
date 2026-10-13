package thecook.model

import thecook.composition.ComponentId
import thecook.filesystem.TargetPath

sealed class OutputSpec(
  val owner: ComponentId,
  val path: TargetPath,
)

class BlockOutput(
  owner: ComponentId,
  path: TargetPath,
  val region: String,
  val content: Provider<List<String>>,
) : OutputSpec(owner, path)

enum class DocumentFormat { JSON, TOML, YAML }

class KeyContribution
  @JvmOverloads
  constructor(
    val path: List<String>,
    val value: Provider<Any?>,
    val adopt: Boolean = false,
    val whenPresent: Boolean = false,
  )

class StructuredOutput(
  owner: ComponentId,
  path: TargetPath,
  val format: DocumentFormat,
  val keys: List<KeyContribution>,
) : OutputSpec(owner, path)

class FileOutput
  @JvmOverloads
  constructor(
    owner: ComponentId,
    path: TargetPath,
    val content: Provider<String>,
    val executable: Boolean = false,
    val adopt: Boolean = false,
  ) : OutputSpec(owner, path)

class SymbolicLinkOutput
  @JvmOverloads
  constructor(
    owner: ComponentId,
    path: TargetPath,
    val target: String,
    val adopt: Boolean = false,
  ) : OutputSpec(owner, path) {
    init {
      require(target.isNotEmpty() && target.none { it.code < 32 || it.code == 127 }) { "Invalid symlink target" }
    }
  }
