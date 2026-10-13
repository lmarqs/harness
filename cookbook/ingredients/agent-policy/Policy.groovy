package thecook.cookbook.ingredients.agentpolicy

import groovy.transform.CompileStatic

/** Immutable policy values. Targets decide how to render their supported controls. */
@CompileStatic
final class Policy {
  final List<String> instructions
  final List<Command> commands

  Policy(List<String> instructions, List<Command> commands) {
    this.instructions = List.copyOf(instructions)
    this.commands = List.copyOf(commands)
  }

  enum Decision {
    ALLOW, ASK, DENY
  }

  /** A literal argv prefix, with no shell interpolation or wildcard syntax. */
  @CompileStatic
  static final class Command {
    final Decision decision
    final List<String> prefix

    Command(Decision decision, List<String> prefix) {
      Objects.requireNonNull(decision, 'decision')
      if (prefix.isEmpty() || prefix.any { String token -> !(token ==~ /[a-zA-Z0-9_\.\/:=@+\-]+/) })
        throw new IllegalArgumentException('Command prefixes require nonempty literal tokens; shell syntax and wildcards are unsupported')
      this.decision = decision
      this.prefix = List.copyOf(prefix)
    }
  }
}
