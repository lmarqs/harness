package thecook.cookbook.targets.claude

import groovy.transform.CompileStatic
import thecook.cookbook.ingredients.agentpolicy.Policy
import thecook.composition.ComponentId
import thecook.filesystem.TargetPath
import thecook.model.*

/** Native Claude file declarations, reused by policy and factory recipes. */
@CompileStatic
final class Claude implements Contributor {
  final ComponentId id
  final List<KeyContribution> settings = []
  TargetPath settingsPath = TargetPath.of('.claude/settings.json')
  TargetPath instructionsPath = TargetPath.of('CLAUDE.md')
  Provider<List<String>> instructions

  Claude(String name = 'claude') {
    id = ComponentId.of(name)
  }

  Claude(Policy policy, boolean adopt = false, String name = 'cookbook.claude') {
    this(name)
    settings.addAll(ClaudePolicy.keys(Objects.requireNonNull(policy), adopt))
    instructions = { ['@AGENTS.md'] }
  }

  @Override List<OutputSpec> outputs() {
    List<OutputSpec> outputs = []
    if (settings) outputs.add(new StructuredOutput(id, settingsPath, DocumentFormat.JSON, List.copyOf(settings)))
    if (instructions != null) outputs.add(new BlockOutput(id, instructionsPath, 'instructions', instructions))
    outputs
  }
}

/** Policy-to-native translation is independent of native file generation. */
@CompileStatic
final class ClaudePolicy {
  private ClaudePolicy() {}

  static List<KeyContribution> keys(Policy policy, boolean adopt) {
    List<KeyContribution> keys = [new KeyContribution(['permissions', 'defaultMode'], { 'acceptEdits' }, adopt)]
    for (Policy.Decision decision : Policy.Decision.values()) {
      List<String> rules = policy.commands.findAll { Policy.Command command -> command.decision == decision }
      .collect { Policy.Command command -> 'Bash(' + command.prefix.join(' ') + ' *)' }
      keys.add(new KeyContribution(['permissions', decision.name().toLowerCase(Locale.ROOT)], { rules }, adopt))
    }
    keys
  }
}
