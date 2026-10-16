package dev.thecook.agents

/**
 * Models the project instructions, policy and skills shared by coding agents in a repository.
 *
 * ## Writes for
 *
 * Markdown instructions in `AGENTS.md`, seeded from a section template that an agent fills in when asked, whose
 * line under each heading says what the section holds; version 1 of the YAML policy at `.agents/policy.yaml`; and skill file trees under
 * `.agents/skills/`, each with `SKILL.md` and its supporting resources.
 *
 * ## Instances
 *
 * One instance covers the shared setup in one directory. Use `at` for a separate setup in another directory;
 * each connected harness must be able to read the shared paths from its own directory.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.agents.Agents
 * import dev.thecook.agents.Claude
 *
 * @ingredient(Agents) def agents
 * @ingredient(Claude) def claude
 *
 * agents.instruct('Run the project checks before delivery.')
 * agents.skills('solid')
 * agents.permit([claude])
 * agents.connect([claude])
 * ```
 *
 * ## With other models
 *
 * `Harness` models, such as `Claude` and `Codex` in this artifact or a harness from another cookbook, translate
 * the shared policy and paths into their own native files.
 * Agents owns the shared layout and computes the relative paths it passes to each harness.
 */
@Ingredient
class Agents {
  private List<String> instructions = []
  private List<String> skills = []
  private boolean instructionsUsed = false
  private boolean policyUsed = false

  /** Adds individual Markdown lines to the shared instructions and requests the instruction file's seed. */
  void instruct(String... lines) { instruct(lines as List) }

  /** Adds a collection of Markdown lines to the shared instructions and requests the instruction file's seed. */
  void instruct(Collection<String> lines) {
    instructions += lines
    instructionsUsed = true
  }

  /**
   * Adds skills packaged with this artifact to the shared skill directory, by name: `solid` (SOLID principles,
   * TDD and clean code), `hexagonal-layout` (business logic boundaries and dependency direction) and
   * `effective-java-core` (Java and Groovy API design). Rejects any other name. Add skills before calling `connect`.
   */
  void skills(String... names) {
    names.each { if (!resources("skills/${it}")) throw new IllegalArgumentException("No packaged skill named ${it}") }
    skills += names.toList()
  }

  /** The shared agent policy at `.agents/policy.yaml`, or the starter when the file is absent. */
  Policy policy() {
    policyUsed = true
    Policy.parse(current('.agents/policy.yaml') ?: Policy.STARTER)
  }

  /** Renders the shared policy into each harness's own permission model. */
  void permit(Collection<Harness> harnesses) {
    def shared = policy()
    harnesses.each { it.permit(shared) }
  }

  /** Points each harness at `AGENTS.md` and the shared skills, by paths relative to where it sits. Call it after adding skills. */
  void connect(Collection<Harness> harnesses) {
    instructionsUsed = true
    harnesses.each { harness ->
      harness.readInstructions(reach(harness, 'AGENTS.md'))
      if (skills) harness.readSkills(reach(harness, '.agents/skills'), skills)
    }
  }

  /**
   * Seeds `AGENTS.md` when absent with the packaged `AGENTS.template.md`: a note telling agents to fill the file in
   * when the developer asks, then sections, related ones grouped under one heading, each with a line saying what it
   * holds and a body in the shape of its answer: a table with labelled rows, or a list of bracketed placeholders.
   * Written once instructions are added or a harness is connected; never owned.
   */
  @Seed(path = 'AGENTS.md')
  String starter() { instructionsUsed ? resource('AGENTS.template.md') : null }

  /** Seeds `.agents/policy.yaml` with the starter policy when absent, once the policy is read; never owned. */
  @Seed(path = '.agents/policy.yaml')
  String starterPolicy() { policyUsed ? Policy.STARTER : null }

  /** The `instructions` section of `AGENTS.md`, when instruction lines are added. */
  @Section(path = 'AGENTS.md', id = 'instructions')
  List<String> instructionsSection() { instructions ?: null }

  /** Whole files under `.agents/skills/<name>/`, when skills are added. */
  @Files(path = '.agents/skills')
  Map<String, String> skillFiles() {
    skills.collectEntries { name ->
      resources("skills/${name}").collectEntries { path, body -> ["${name}/${path}", body] }
    } ?: null
  }

  private String reach(Harness harness, String path) {
    Path.of(harness.directory).normalize().relativize(Path.of(directory, path).normalize()).toString()
  }
}

/** An agent program that reads the shared setup through its own native files. Agents drives it. */
interface Harness {
  /** The harness name used to select the agent program, such as `claude` or `codex`. */
  String getName()

  /** The harness instance's directory relative to the destination, with `.` at its root. */
  String getDirectory()

  /** Renders the shared policy into the harness's own permission model. */
  void permit(Policy policy)

  /** Makes the harness read the shared instructions at `path`, relative to its directory. */
  void readInstructions(String path)

  /** Makes the harness find the named shared skills under `path`, relative to its directory. */
  void readSkills(String path, List<String> names)
}

/** Version 1 of the shared policy: instructions and literal command prefixes with a decision each. */
class Policy {
  /** The version 1 YAML starter with no instructions or command permissions. */
  static final String STARTER = 'version: 1\ninstructions: []\ncommands: {}\n'

  /** Shared instruction lines read from the policy's `instructions` field. */
  List<String> instructions = []

  /** Command prefixes the harness permits. */
  List<Command> allow = []

  /** Command prefixes for which the harness requests approval. */
  List<Command> ask = []

  /** Command prefixes the harness refuses. */
  List<Command> deny = []

  /** Parses YAML policy text; rejects a version other than 1, unknown top-level fields and invalid command prefixes. */
  static Policy parse(String text) {
    def data = new YamlSlurper().parseText(text)
    if (data.version != 1) throw new IllegalArgumentException('.agents/policy.yaml: policy version must be 1')
    def unknown = data.keySet() - ['version', 'instructions', 'commands']
    if (unknown) throw new IllegalArgumentException(".agents/policy.yaml: unknown field ${unknown}")
    new Policy(
      instructions: data.instructions ?: [],
      allow: commands(data, 'allow'), ask: commands(data, 'ask'), deny: commands(data, 'deny'))
  }

  private static List<Command> commands(Map data, String decision) {
    (data.commands?.get(decision) ?: []).collect { new Command(prefix: it) }
  }
}

/** A literal argv prefix with no shell syntax, translated by each harness into its native permission rules. */
class Command {
  /** The ordered literal argv tokens that a permitted, prompted or forbidden command begins with. */
  List<String> prefix

  /**
   * Builds a command from `prefix`; rejects an empty prefix or tokens containing characters other than
   * letters, digits, underscore, dot, slash, colon, equals, at sign, plus and hyphen.
   */
  Command(Map args) {
    prefix = args.prefix
    if (!prefix || prefix.any { !(it ==~ /[a-zA-Z0-9_.\/:=@+-]+/) })
      throw new IllegalArgumentException("Command prefix must be literal tokens: ${prefix}")
  }
}
