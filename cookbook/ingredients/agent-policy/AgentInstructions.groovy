package thecook.cookbook.ingredients.agentpolicy

import groovy.transform.CompileStatic
import thecook.composition.ComponentId
import thecook.filesystem.TargetPath
import thecook.model.*

/** Agent guidance text. Native permission enforcement belongs to the selected agent recipe. */
@CompileStatic
final class AgentInstructions implements Contributor {
  final ComponentId id
  final TargetPath path
  final List<String> instructions = []

  AgentInstructions(String name = 'agent-policy', String path = 'AGENTS.md') {
    id = ComponentId.of(name)
    this.path = TargetPath.of(path)
  }

  List<OutputSpec> outputs() {
    Provider<List<String>> content = { new ArrayList<String>(instructions) }
    [new BlockOutput(id, path, 'instructions', content)]
  }
}
