@Ingredient
class Agents {
  List<String> instructions = []
  List<Skill> skills = []
  boolean instructionsUsed = false
  boolean policyUsed = false

  void instruct(String... lines) { instruct(lines as List) }

  void instruct(Collection<String> lines) {
    instructions += lines
    instructionsUsed = true
  }

  void skills(Skill... added) { skills += added }

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
      if (skills) harness.readSkills(reach(harness, '.agents/skills'), skills*.name)
    }
  }

  @Seed(path = 'AGENTS.md')
  String starter() { instructionsUsed ? '# Agent instructions\n' : null }

  @Seed(path = '.agents/policy.yaml')
  String starterPolicy() { policyUsed ? Policy.STARTER : null }

  @Section(path = 'AGENTS.md', id = 'instructions')
  List<String> instructionsSection() { instructions ?: null }

  @Files(path = '.agents/skills')
  Map<String, String> skillFiles() {
    skills.collectEntries { skill ->
      skill.files.collectEntries { path, body -> ["${skill.name}/${path}", body] }
    } ?: null
  }

  private String reach(Harness harness, String path) {
    Path.of(harness.directory).normalize().relativize(Path.of(directory, path).normalize()).toString()
  }
}

/** An agent program that reads the shared setup through its own native files. Agents drives it. */
interface Harness {
  String getName()
  String getDirectory()

  /** Renders the shared policy into the harness's own permission model. */
  void permit(Policy policy)

  /** Makes the harness read the shared instructions at `path`, relative to its directory. */
  void readInstructions(String path)

  /** Makes the harness find the named shared skills under `path`, relative to its directory. */
  void readSkills(String path, List<String> names)
}

/** Skill content another cookbook ships: every file it packages under `skill/`, `SKILL.md` among them. */
trait Skill {
  abstract String getName()

  Map<String, String> getFiles() { resources('skill') }
}

/** Version 1 of the shared policy: instructions and literal command prefixes with a decision each. */
class Policy {
  static final String STARTER = 'version: 1\ninstructions: []\ncommands: {}\n'

  List<String> instructions = []
  List<Command> allow = [], ask = [], deny = []

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

class Command {
  List<String> prefix   // literal argv tokens, no shell syntax

  Command(Map args) {
    prefix = args.prefix
    if (!prefix || prefix.any { !(it ==~ /[a-zA-Z0-9_.\/:=@+-]+/) })
      throw new IllegalArgumentException("Command prefix must be literal tokens: ${prefix}")
  }
}
