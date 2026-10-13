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
  PlanHandle remove(Path repository, Set<String> applications) {
    def target = new Repository(repository.toFile())
    Map state = StateSchema.current(target.state())
    Map<String, Map> membership = (Map<String, Map>) state.get('applications')
    Set<String> selected = membership.keySet().findAll { String id -> applications.contains(id) } as Set<String>
    if (selected.isEmpty()) {
      return new NoChangePlan()
    }
    Map<String, List<thecook.rendering.RenderedFile>> removals = selected.collectEntries { String id -> [(id): []] }
    new NativePlan(new Reconciler().plan(target, new RenderTree([], removals), false, true))
  }

  private static class NoChangePlan implements PlanHandle {
    @Override
    List<String> getChanges() {
      []
    }

    @Override
    void save(Path file) {
      throw new UnsupportedOperationException('Nothing to save')
    }

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
