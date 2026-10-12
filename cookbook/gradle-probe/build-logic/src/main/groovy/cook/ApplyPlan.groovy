package cook

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

abstract class ApplyPlan extends DefaultTask {
    @InputFile abstract RegularFileProperty getPlanFile()
    @Internal File repository

    @TaskAction void apply() {
        def plan = new JsonSlurper().parse(planFile.get().asFile)
        if (plan.repository != repository.canonicalPath) throw new GradleException('Plan belongs to a different repository; run plan first.')
        def writes = plan.changes.collect { change ->
            def file = new File(repository, change.path).canonicalFile
            if (!file.toPath().startsWith(repository.canonicalFile.toPath())) throw new GradleException('Plan path escapes repository')
            def current = file.exists() ? RepositoryPlan.hash(file.bytes) : null
            if (current != change.before) throw new GradleException("Changed since preview: ${change.path}; nothing written. Run plan again.")
            [file: file, bytes: change.content == null ? null : change.content.decodeBase64()]
        }
        // Validate every file before the first write. Crash rollback is outside this disposable probe.
        writes.each { write ->
            if (write.bytes == null) java.nio.file.Files.delete(write.file.toPath())
            else { write.file.parentFile.mkdirs(); write.file.bytes = write.bytes }
        }
        logger.lifecycle("Applied ${writes.size()} file changes to ${repository}.")
    }
}
