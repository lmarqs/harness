package cook

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.*

abstract class StagedMigration extends DefaultTask {
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getSourceDirectory()
    @OutputDirectory abstract DirectoryProperty getDestinationDirectory()
    @Inject abstract FileSystemOperations getFileSystem()

    @TaskAction void stage() {
        def destination = destinationDirectory.get().asFile
        fileSystem.sync { from(sourceDirectory); into(destination) }
        migrate(destination)
    }

    abstract void migrate(File directory)
}
