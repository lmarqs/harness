package thecook.application

import thecook.rendering.RenderedPlan
import java.nio.file.Path

interface CookbookLoader {
  fun load(
    repository: Path,
    script: Path,
    libraries: List<Path>,
    preferences: Map<String, Any>,
  ): RenderedPlan
}

interface Planning {
  fun plan(
    repository: Path,
    outputs: RenderedPlan,
  ): PlanHandle

  fun load(
    repository: Path,
    savedPlan: Path,
  ): PlanHandle

  fun remove(repository: Path, recipe: Path, harnesses: List<String>): PlanHandle
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
      preferences: Map<String, Any>,
    ): PlanHandle = planning.plan(repository, loader.load(repository, script, libraries, preferences))

    fun cook(
      repository: Path,
      script: Path,
      libraries: List<Path>,
      preferences: Map<String, Any>,
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

    fun remove(repository: Path, recipe: Path, harnesses: List<String>, dryRun: Boolean): List<String> {
      val pending = planning.remove(repository, recipe, harnesses)
      if (!dryRun) pending.apply()
      return pending.changes
    }
  }
