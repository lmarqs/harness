package thecook.gradle

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import thecook.reconciliation.PatchWriter
import thecook.reconciliation.Reconciler

@DisableCachingByDefault(because = 'Reconciliation observes an external repository and its ownership state')
abstract class CookPlanTask extends DefaultTask {
  @Internal CookExtension configuration
  @OutputFile abstract RegularFileProperty getPlanFile()
  @OutputFile abstract RegularFileProperty getPatchFile()
  @OutputFile abstract RegularFileProperty getTreeFile()

  @TaskAction void preview() {
    def tree = configuration.render()
    def plan = new Reconciler().plan(new thecook.filesystem.Repository(configuration.repository), tree)
    plan.save(planFile.get().asFile)
    patchFile.get().asFile.setText(new PatchWriter().write(plan), 'UTF-8')
    treeFile.get().asFile.setText(JsonOutput.prettyPrint(JsonOutput.toJson(tree.describe())) + '\n', 'UTF-8')
    logger.lifecycle("${plan.changes.size()} file changes; ${patchFile.get().asFile}")
    plan.changes.each { logger.lifecycle(it.path) }
  }
}
