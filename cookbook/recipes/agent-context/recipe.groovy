@param(['harness'], choices = ['claude', 'codex'], many = true, doc = 'Harnesses that read the shared instructions')
String[] harness

@ingredient(Agents) def agents
@ingredient(Claude) def claude
@ingredient(Codex) def codex

agents.connect([claude, codex].findAll { it.name in harness })
