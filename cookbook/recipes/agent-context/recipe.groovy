@param(['harness'], choices = ['claude', 'codex'], many = true, doc = 'Harnesses that read the shared instructions')
String[] harness

@ingredient(Agents) def agents
@ingredient(Claude) def claude

// Agents seeds AGENTS.md. Codex reads it natively; Claude needs the import.
if ('claude' in harness) claude.importAgents()
