import thecook.cookbook.git.Git
import thecook.cookbook.policy.AgentPolicy
import thecook.cookbook.skills.Skills
import thecook.cookbook.thecook.TheCook
import thecook.model.Batch

def source = new File(preferences.skillsHome ?: new File(System.getProperty('user.home'), '.agents/skills').path)
def skills = new Skills()
skills.link('solid', new File(source, 'solid').absolutePath)
skills.link('hexagonal-layout', new File(source, 'hexagonal-layout').absolutePath)

def policy = new AgentPolicy()
policy.instructions.addAll([
    '# Working on the-cook',
    '',
    'Use .agents/skills/solid for implementation and review.',
    'Use .agents/skills/hexagonal-layout for application boundaries and adapters.',
    '',
    'Core contracts and the CLI are Kotlin; cookbook suppliers and repository recipes are Groovy.',
    'Keep application rules independent of filesystem and terminal adapters.',
    'Edit recipes/agent-setup/codex.groovy to change this setup, then run ./the-cook cook.',
    'Build and verify changes with ./gradlew check cliBuild.',
    'Preview generated changes with ./the-cook cook --dry-run.',
    'Keep work on main and leave changes uncommitted unless explicitly requested.'
])

def git = new Git()
def cook = new TheCook()
git.ignore.add(cook.files.local)
git.ignore.add(skills.files.local)

new Batch('the-cook').include(cook).include(git).include(skills).include(policy)
