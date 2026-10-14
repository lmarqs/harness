@Ingredient
class Agents {
  List<String> instructions = []
  List<Skill> skills = []

  void instruct(String... lines) { instructions += lines }

  void instruct(Collection<String> lines) { instructions += lines }

  void skills(Skill... added) { skills += added }

  /** The shared agent policy at `.agents/policy.yaml`, or the starter when the file is absent. */
  Policy policy() { Policy.read(new File(directory, '.agents/policy.yaml')) }

  @Seed(path = 'AGENTS.md')
  String starter() { '# Agent instructions\n' }

  @Seed(path = '.agents/policy.yaml')
  String starterPolicy() { Policy.STARTER }

  @Section(path = 'AGENTS.md', id = 'instructions')
  List<String> instructionsSection() { instructions ?: null }

  @Files(path = '.agents/skills')
  Map<String, String> skillFiles() {
    skills.collectEntries { skill ->
      skill.files.collectEntries { path, body -> ["${skill.name}/${path}", body] }
    } ?: null
  }
}

/** Content another cookbook ships: a skill directory with `SKILL.md` first. */
interface Skill {
  String getName()
  Map<String, String> getFiles()
}

/** Version 1 of the shared policy: instructions and literal command prefixes with a decision each. */
class Policy {
  static final String STARTER = 'version: 1\ninstructions: []\ncommands: {}\n'

  List<String> instructions = []
  List<Command> allow = [], ask = [], deny = []

  static Policy read(File file) {
    def data = new YamlSlurper().parseText(file.isFile() ? file.text : STARTER)
    if (data.version != 1) throw new IllegalArgumentException("${file}: policy version must be 1")
    def unknown = data.keySet() - ['version', 'instructions', 'commands']
    if (unknown) throw new IllegalArgumentException("${file}: unknown field ${unknown}")
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
