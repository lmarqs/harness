package thecook.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import thecook.filesystem.Repository
import thecook.filesystem.Transaction
import thecook.reconciliation.ChangePlan

class CookPlugin implements Plugin<Project> {
    void apply(Project project) {
        def configuration = project.extensions.create('cook', CookExtension)
        configuration.repository = project.layout.projectDirectory.asFile
        configuration.cookbookDirectory = project.file('cookbook')
        def saved = project.layout.buildDirectory.file('cook/plan.json')
        def preview = project.tasks.register('cookPlan', CookPlanTask) {
            group = 'cook'; description = 'Render the recipe and save a reviewable plan without changing the target'
            it.configuration = configuration
            planFile = saved
            patchFile = project.layout.buildDirectory.file('cook/changes.patch')
            treeFile = project.layout.buildDirectory.file('cook/tree.json')
            outputs.upToDateWhen { false }
            dependsOn(project.provider { configuration.ingredientArtifacts })
        }
        project.tasks.register('cookApply') {
            group = 'cook'; description = 'Apply the saved plan; reject drift without recalculating the preview'
            doLast { ChangePlan.load(new Repository(configuration.repository), saved.get().asFile).apply() }
        }
        project.tasks.register('cookRender') {
            group = 'cook'; description = 'Render and apply the current recipe in one invocation'
            dependsOn(preview)
            doLast { ChangePlan.load(new Repository(configuration.repository), saved.get().asFile).apply() }
        }
        project.tasks.register('cookEjectPlan') {
            group = 'cook'; description = 'Save a plan to retain native files and release Cook ownership'
            doLast { configuration.kitchen().eject().save(saved.get().asFile) }
        }
        project.tasks.register('cookRecover') {
            group = 'cook'; description = 'Restore the before state of an interrupted filesystem transaction'
            doLast { new Transaction(new Repository(configuration.repository)).recover() }
        }
    }
}
