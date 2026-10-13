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
