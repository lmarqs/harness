import dev.thecook.Beads
import dev.thecook.Git
import dev.thecook.Mise
import dev.thecook.Ralph

@param(['agent'], default = 'codex', doc = 'The agent Ralph drives, by its Ralph plugin name') String agent
@param(['tracker'], choices = ['json', 'beads'], default = 'json', doc = 'Where Ralph takes its stories from') String tracker
@param(['mode'], choices = ['standalone', 'mise'], default = 'standalone',
       doc = 'Run Ralph through its own runner at .ralph-tui/run, or through the mise task ralph:run') String mode
@param(['prd'], default = 'tasks/prd.json', doc = 'The PRD file, for the json tracker') String prd
@param(['workers'], default = 1, doc = 'Parallel workers; more than one turns parallel mode on') int workers
@param(['max-iterations'], default = 10, doc = 'Iterations before Ralph stops') int maxIterations
@param(['auto-commit'], default = false, doc = 'Commit after each iteration') boolean autoCommit
@param(['headless'], default = false, doc = 'Run without the TUI') boolean headless
@param(['issue-prefix'], default = null, doc = 'The Beads issue prefix, for the beads tracker') String issuePrefix

@ingredient(Ralph) def ralph
@ingredient(Beads) def beads
@ingredient(Mise) def mise
@ingredient(Git) def git

ralph.configure(agent: agent, tracker: tracker, prd: prd, workers: workers,
                maxIterations: maxIterations, autoCommit: autoCommit, headless: headless)
git.ignore(ralph.localFiles + ralph.ephemeralFiles)

if (mode == 'mise') {
  mise.tools(ralph.tools)
  mise.script('ralph:run', ralph.runScript())
} else {
  ralph.installRunner()
}

if (tracker == 'beads') {
  beads.track(issuePrefix)
  git.ignore(beads.localFiles + beads.ephemeralFiles)
  mise.script('beads:ready', beads.readyScript())
}
