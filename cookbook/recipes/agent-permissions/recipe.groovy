@param(['harness'], choices = ['claude', 'codex'], many = true, doc = 'Harnesses that receive the shared policy')
String[] harness

@ingredient(Agents) def agents
@ingredient(Claude) def claude
@ingredient(Codex) def codex

def policy = agents.policy()
if ('claude' in harness) claude.permit(policy)
if ('codex' in harness) codex.permit(policy)
