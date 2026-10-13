package thecook.cookbook.targets.claude

import groovy.transform.CompileStatic
import thecook.cookbook.ingredients.agentpolicy.Policy
import thecook.composition.ComponentId
import thecook.filesystem.TargetPath
import thecook.model.*

/** Claude native command controls and an import of the shared instructions. */
@CompileStatic
final class Claude implements Contributor {
    final ComponentId id
    private final Policy policy
    private final boolean adopt

    Claude(Policy policy, boolean adopt = false, String name = 'cookbook.claude') {
        id = ComponentId.of(name)
        this.policy = Objects.requireNonNull(policy)
        this.adopt = adopt
    }

    @Override List<OutputSpec> outputs() {
        List<KeyContribution> keys = [
            new KeyContribution(['permissions', 'defaultMode'], { 'acceptEdits' }, adopt)
        ]
        for (Policy.Decision decision : Policy.Decision.values()) {
            List<String> rules = policy.commands.findAll { Policy.Command command -> command.decision == decision }
                .collect { Policy.Command command -> 'Bash(' + command.prefix.join(' ') + ' *)' }
            keys.add(new KeyContribution(['permissions', decision.name().toLowerCase(Locale.ROOT)], { rules }, adopt))
        }
        [new StructuredOutput(id, TargetPath.of('.claude/settings.json'), DocumentFormat.JSON, keys),
         new BlockOutput(id, TargetPath.of('CLAUDE.md'), 'instructions', { ['@AGENTS.md'] })]
    }
}
