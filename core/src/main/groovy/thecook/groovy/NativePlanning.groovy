package thecook.groovy

import groovy.transform.CompileStatic
import java.nio.file.Path
import thecook.application.Planning
import thecook.application.PlanHandle
import thecook.filesystem.Repository
import thecook.reconciliation.Reconciler
import thecook.rendering.RenderTree
import thecook.rendering.RenderedPlan
import thecook.reconciliation.ChangePlan
import thecook.reconciliation.StateSchema

@CompileStatic
class NativePlanning implements Planning {
  @Override
  PlanHandle plan(Path repository, RenderedPlan outputs) {
    new NativePlan(new Reconciler().plan(new Repository(repository.toFile()),
        new RenderTree(outputs.outputs, outputs.applications, outputs.provenance)))
  }

  @Override
  PlanHandle load(Path repository, Path savedPlan) {
    new NativePlan(ChangePlan.load(new Repository(repository.toFile()), savedPlan.toFile()))
  }

  @Override
  PlanHandle remove(Path repository, Path recipe, List<String> harnesses) {
    def target = new Repository(repository.toFile())
    Map state = StateSchema.current(target.state())
    Map<String, Map> membership = (Map<String, Map>) state.get('applications')
    Map<String, Map> provenance = (Map<String, Map>) state.get('provenance')
    String file = recipe.fileName.toString()
    String name = file == 'recipe.groovy' ? recipe.parent.fileName.toString() : file.replaceFirst(/\.groovy$/, '')
    boolean init = name in ['cookbook', 'init']
    boolean permission = name in ['permission', 'permissions', 'claude', 'codex'] || init
    List<String> selectedHarnesses = name in ['claude', 'codex'] ? [name] : (harnesses ?: ['claude', 'codex'])
    Set<String> initApplications = ['agentic.init/cookbook', 'agentic.init/the-cook', 'agentic.init/git',
        'agentic.init/skills', 'agentic.init/claude-skills', 'agentic.init/agent-policy'] as Set<String>
    String path = recipe.toString().replace('\\', '/')
    int recipesIndex = path.lastIndexOf('/recipes/')
    String source = recipesIndex < 0 ? path : path.substring(recipesIndex + 1)
    Set<String> selected = membership.keySet().findAll { String id ->
      (init && initApplications.contains(id)) ||
          (permission && selectedHarnesses.any { String harness -> id == 'agentic.permissions/' + harness }) ||
          (provenance[id]?.get('recipe') in [path, source] && harnesses.isEmpty())
    }
    if (selected.isEmpty()) return new NoChangePlan()
    Map<String, List<thecook.rendering.RenderedFile>> applications = selected.collectEntries { String id -> [(id): []] }
    new NativePlan(new Reconciler().plan(target, new RenderTree([], applications), false, true))
  }

  private static class NoChangePlan implements PlanHandle {
    @Override
    List<String> getChanges() { [] }

    @Override
    void save(Path file) { throw new UnsupportedOperationException('Nothing to save') }

    @Override
    void apply() { }
  }

  private static class NativePlan implements PlanHandle {
    private final ChangePlan plan

    NativePlan(ChangePlan plan) {
      this.plan = plan
    }

    @Override
    List<String> getChanges() {
      plan.changes.collect { Object change -> ((Map) change).get('path').toString() }
    }

    @Override
    void save(Path file) {
      plan.save(file.toFile())
    }

    @Override
    void apply() {
      plan.apply()
    }
  }
}
