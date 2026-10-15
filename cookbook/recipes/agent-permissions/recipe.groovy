import dev.thecook.Agents
import dev.thecook.Claude
import dev.thecook.Codex

@param(['harness'], choices = ['claude', 'codex'], many = true, doc = 'Harnesses that receive the shared policy')
String[] harness

@ingredient(Agents) def agents
@ingredient(Claude) def claude
@ingredient(Codex) def codex

agents.permit([claude, codex].findAll { it.name in harness })
