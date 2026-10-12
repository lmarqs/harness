package cook

import groovy.json.JsonOutput
import javax.inject.Inject
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.*
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations

abstract class RepositoryPlan extends DefaultTask {
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE) abstract DirectoryProperty getBeforeDirectory()
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE) abstract DirectoryProperty getAfterDirectory()
    @Internal File repository
    @OutputFile abstract RegularFileProperty getPlanFile()
    @OutputFile abstract RegularFileProperty getPatchFile()
    @Inject abstract ExecOperations getExecOperations()

    @Input String getTargetPath() { repository.canonicalPath }

    static Map<String, File> files(File root) {
        def result = [:]
        root.eachFileRecurse { file ->
            if (java.nio.file.Files.isSymbolicLink(file.toPath())) throw new IllegalArgumentException("Symlink outside this probe's scope: ${file}")
            if (file.isFile()) result[root.toPath().relativize(file.toPath()).toString()] = file
        }
        result
    }

    static String hash(byte[] value) { MessageDigest.getInstance('SHA-256').digest(value).encodeHex().toString() }

    @TaskAction void plan() {
        def before = files(beforeDirectory.get().asFile)
        def after = files(afterDirectory.get().asFile)
        def changes = (before.keySet() + after.keySet()).unique().sort().findResults { path ->
            byte[] old = before[path]?.bytes
            byte[] next = after[path]?.bytes
            if (Arrays.equals(old, next)) return null
            [path: path, before: old == null ? null : hash(old), content: next == null ? null : next.encodeBase64().toString()]
        }
        def output = new ByteArrayOutputStream()
        def diff = execOperations.exec {
            commandLine 'git', 'diff', '--no-index', '--no-ext-diff', '--', beforeDirectory.get().asFile, afterDirectory.get().asFile
            standardOutput = output
            ignoreExitValue = true
        }
        if (diff.exitValue > 1) throw new IllegalStateException('git diff failed')
        def patch = output.toString('UTF-8')
            .replace('a' + beforeDirectory.get().asFile.absolutePath + '/', 'a/')
            .replace('b' + afterDirectory.get().asFile.absolutePath + '/', 'b/')
            .replace('a' + afterDirectory.get().asFile.absolutePath + '/', 'a/')
            .replace('b' + beforeDirectory.get().asFile.absolutePath + '/', 'b/')
        patchFile.get().asFile.text = patch
        planFile.get().asFile.text = JsonOutput.prettyPrint(JsonOutput.toJson([repository: targetPath, changes: changes])) + '\n'
        logger.lifecycle(patch ?: 'No changes.')
        logger.lifecycle("${changes.size()} changed files. Saved plan: ${planFile.get().asFile}")
    }
}
