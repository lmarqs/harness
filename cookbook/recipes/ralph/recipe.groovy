@param(['agent'], default = 'codex') String agent
@param(['tracker'], choices = ['json', 'beads'], default = 'json') String tracker
@param(['mode'], choices = ['standalone', 'mise'], default = 'standalone') String mode
@param(['prd'], default = 'tasks/prd.json', doc = 'PRD file for the json tracker') String prd
@param(['workers'], default = 1) int workers
@param(['max-iterations'], default = 10) int maxIterations
@param(['auto-commit'], default = false) boolean autoCommit
@param(['headless'], default = false) boolean headless
@param(['issue-prefix'], default = null, doc = 'Beads issue prefix, for the beads tracker') String issuePrefix

@ingredient(Ralph) def ralph
@ingredient(Beads) def beads
@ingredient(Mise) def mise
@ingredient(Git) def git

ralph.configure(agent: agent, tracker: tracker, mode: mode, prd: prd, workers: workers,
                maxIterations: maxIterations, autoCommit: autoCommit, headless: headless)
git.ignore(ralph.localFiles + ralph.ephemeralFiles)

if (tracker == 'beads') {
  beads.track(issuePrefix)
  git.ignore(beads.localFiles + beads.ephemeralFiles)
  mise.script('beads:ready', beads.readyScript())
}

if (mode == 'mise') {
  mise.tools(ralph.tools)
  mise.script('ralph:run', ralph.runScript())
}
