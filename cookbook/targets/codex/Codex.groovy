package thecook.cookbook.targets.codex

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import thecook.cookbook.ingredients.agentpolicy.Policy
import thecook.composition.ComponentId
import thecook.filesystem.TargetPath
import thecook.model.*

/** Native Codex file declarations. Policy translation is a separate module below. */
@CompileStatic
final class Codex implements Contributor {
  final ComponentId id
  final List<KeyContribution> config = []
  final List<KeyContribution> hooks = []
  TargetPath configPath = TargetPath.of('.codex/config.toml')
  TargetPath hooksPath = TargetPath.of('.codex/hooks.json')
  TargetPath instructionsPath = TargetPath.of('AGENTS.md')
  Provider<List<String>> instructions
  private final List<OutputSpec> ruleOutputs = []
  private final boolean adopt

  Codex(String name = 'codex', boolean adopt = false) {
    id = ComponentId.of(name)
    this.adopt = adopt
  }

  Codex(Policy policy, boolean adopt = false, String name = 'cookbook.codex') {
    this(name, adopt)
    config.addAll(CodexPolicy.keys(Objects.requireNonNull(policy), adopt))
    ruleBlock('cookbook.rules', 'commands', { CodexPolicy.rules(policy) })
  }

  void rule(String name, Provider<String> content) {
    ruleOutputs.add(new FileOutput(id, rulePath(name), content, false, adopt))
  }

  void ruleBlock(String name, String region, Provider<List<String>> content) {
    ruleOutputs.add(new BlockOutput(id, rulePath(name), region, content))
  }

  private static TargetPath rulePath(String name) {
    if (!(name ==~ /[a-zA-Z0-9_.-]+/) || name in ['.', '..'])
      throw new IllegalArgumentException('Codex rule file requires a simple file name')
    TargetPath.of('.codex/rules/' + name)
  }

  @Override List<OutputSpec> outputs() {
    List<OutputSpec> outputs = []
    if (config) outputs.add(new StructuredOutput(id, configPath, DocumentFormat.TOML, List.copyOf(config)))
    if (hooks) outputs.add(new StructuredOutput(id, hooksPath, DocumentFormat.JSON, List.copyOf(hooks)))
    if (instructions != null) outputs.add(new BlockOutput(id, instructionsPath, 'instructions', instructions))
    outputs.addAll(ruleOutputs)
    outputs
  }
}

/** Translates policy values into native Codex settings and rule syntax. */
@CompileStatic
final class CodexPolicy {
  private CodexPolicy() {}

  static List<KeyContribution> keys(Policy policy, boolean adopt) {
    [
      new KeyContribution(['sandbox_mode'], { 'workspace-write' }, adopt),
      new KeyContribution(['approval_policy'], { 'on-request' }, adopt),
      new KeyContribution(['sandbox_workspace_write', 'network_access'], { false }, adopt)
    ]
  }

  static List<String> rules(Policy policy) {
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
    lines
  }
}
