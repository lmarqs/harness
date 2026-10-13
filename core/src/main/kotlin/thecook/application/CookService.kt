package thecook.application

import thecook.rendering.RenderedFile
import java.nio.file.Path

interface CookbookLoader {
  fun load(
    repository: Path,
    script: Path,
    libraries: List<Path>,
    preferences: Map<String, String>,
  ): List<RenderedFile>
}

interface Planning {
  fun plan(
    repository: Path,
    outputs: List<RenderedFile>,
  ): PlanHandle

  fun load(
    repository: Path,
    savedPlan: Path,
  ): PlanHandle
}

interface PlanHandle {
  val changes: List<String>

  fun save(file: Path)

  fun apply()
}

interface RepositoryInitializer {
  fun initialize(repository: Path): Path
}

class CookService
  @JvmOverloads
  constructor(
    private val loader: CookbookLoader,
    private val planning: Planning,
    private val initializer: RepositoryInitializer? = null,
  ) {
    fun initialize(repository: Path): Path =
      requireNotNull(initializer) { "Repository initialization is unavailable" }.initialize(repository)

    fun plan(
      repository: Path,
      script: Path,
      libraries: List<Path>,
      preferences: Map<String, String>,
    ): PlanHandle = planning.plan(repository, loader.load(repository, script, libraries, preferences))

    fun cook(
      repository: Path,
      script: Path,
      libraries: List<Path>,
      preferences: Map<String, String>,
      dryRun: Boolean,
    ): List<String> {
      val pending = plan(repository, script, libraries, preferences)
      if (!dryRun) pending.apply()
      return pending.changes
    }

    fun apply(
      repository: Path,
      savedPlan: Path,
    ) {
      planning.load(repository, savedPlan).apply()
    }
  }
