package thecook.cli.filesystem

import thecook.application.RepositoryInitializer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class FileRepositoryInitializer : RepositoryInitializer {
    override fun initialize(repository: Path): Path {
        require(Files.isDirectory(repository)) { "Repository directory does not exist: $repository" }
        val script = repository.resolve("cook.groovy")
        if (Files.exists(script, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(script, LinkOption.NOFOLLOW_LINKS)) { "Expected a regular file: $script" }
            return script
        }
        val template = requireNotNull(javaClass.getResourceAsStream("/thecook/init/cook.groovy"))
            .use { it.readAllBytes() }
        Files.write(script, template, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        return script
    }
}
