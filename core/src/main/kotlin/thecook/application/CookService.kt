package thecook.application

import java.nio.file.Path
import thecook.model.Batch

interface CookbookLoader {
    fun load(script: Path, libraries: List<Path>, preferences: Map<String, String>): Batch
}

interface Planning {
    fun plan(repository: Path, batch: Batch): PlanHandle
    fun load(repository: Path, savedPlan: Path): PlanHandle
}

interface PlanHandle {
    val changes: List<String>
    fun save(file: Path)
    fun apply()
}

interface RepositoryInitializer {
    fun initialize(repository: Path): Path
}

class CookService @JvmOverloads constructor(
    private val loader: CookbookLoader,
    private val planning: Planning,
    private val initializer: RepositoryInitializer? = null,
) {
    fun initialize(repository: Path): Path =
        requireNotNull(initializer) { "Repository initialization is unavailable" }.initialize(repository)

    fun plan(repository: Path, script: Path, libraries: List<Path>, preferences: Map<String, String>): PlanHandle =
        planning.plan(repository, loader.load(script, libraries, preferences))

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

    fun apply(repository: Path, savedPlan: Path) {
        planning.load(repository, savedPlan).apply()
    }
}
