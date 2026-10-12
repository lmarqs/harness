package thecook.cookbook.policy

import thecook.annotations.Ingredient
import thecook.composition.ComponentId
import thecook.filesystem.TargetPath
import thecook.model.*

/** Agent guidance text. Native permission enforcement belongs to the selected target. */
@Ingredient('agent-policy')
class AgentPolicy implements Contributor {
    final ComponentId id
    final TargetPath path
    final List<String> instructions = []

    AgentPolicy(String name = 'agent-policy', String path = 'AGENTS.md') {
        id = ComponentId.of(name)
        this.path = TargetPath.of(path)
    }

    List<OutputSpec> outputs() {
        Provider<List<String>> content = { new ArrayList<String>(instructions) }
        [new BlockOutput(id, path, 'instructions', content)]
    }
}
