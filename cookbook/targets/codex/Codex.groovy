package thecook.cookbook.targets.codex

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import thecook.cookbook.ingredients.agentpolicy.Policy
import thecook.composition.ComponentId
import thecook.filesystem.TargetPath
import thecook.model.*

/** Codex project settings and command rules. Rules govern requests outside its sandbox. */
@CompileStatic
final class Codex implements Contributor {
    final ComponentId id
    private final Policy policy
    private final boolean adopt

    Codex(Policy policy, boolean adopt = false, String name = 'cookbook.codex') {
        id = ComponentId.of(name)
        this.policy = Objects.requireNonNull(policy)
        this.adopt = adopt
    }

    @Override List<OutputSpec> outputs() {
        List<String> lines = [
            '# Generated from the selected policy by ./cook cookbook.',
            '# These literal prefix rules govern execution outside the Codex sandbox.',
            '# They do not inspect the contents of scripts or enforce instructions as OS controls.'
        ]
        for (Policy.Command command : policy.commands) {
            String decision = command.decision == Policy.Decision.DENY ? 'forbidden' :
                command.decision == Policy.Decision.ASK ? 'prompt' : 'allow'
            lines.add('prefix_rule(pattern = ' + JsonOutput.toJson(command.prefix) +
                ', decision = ' + JsonOutput.toJson(decision) + ')')
        }
        [new StructuredOutput(id, TargetPath.of('.codex/config.toml'), DocumentFormat.TOML, [
            new KeyContribution(['sandbox_mode'], { 'workspace-write' }, adopt),
            new KeyContribution(['approval_policy'], { 'on-request' }, adopt),
            new KeyContribution(['sandbox_workspace_write', 'network_access'], { false }, adopt)
        ]), new BlockOutput(id, TargetPath.of('.codex/rules/cookbook.rules'), 'commands', { lines })]
    }
}
