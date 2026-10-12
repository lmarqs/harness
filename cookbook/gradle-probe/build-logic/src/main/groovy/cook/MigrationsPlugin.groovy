package cook

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Sync

class MigrationsPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.tasks.register('snapshot', Sync) {
            into(project.layout.buildDirectory.dir('stages/before'))
        }
        project.tasks.register('plan', RepositoryPlan) {
            group = 'cook'
            description = 'Stage migrations and print a diff without editing the repository.'
            planFile = project.layout.buildDirectory.file('reports/plan.json')
            patchFile = project.layout.buildDirectory.file('reports/changes.patch')
        }
        project.tasks.register('applyPlan', ApplyPlan) {
            group = 'cook'
            description = 'Apply the saved plan; reject changes made since its preview.'
            // Deliberately no dependency on plan: applying must not recompute a reviewed diff.
            planFile = project.layout.buildDirectory.file('reports/plan.json')
        }
    }
}
