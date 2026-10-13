package thecook.filesystem

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class Transaction {
  final Repository repository
  final AtomicFiles files

  Transaction(Repository repository, AtomicFiles files = new AtomicFiles()) {
    this.repository = repository; this.files = files
  }
  void apply(Map proposal) {
    thecook.reconciliation.PlanSchema.validate(proposal)
    if (proposal.changes.isEmpty() && !proposal.stateBefore.exists && proposal.stateAfter.files.isEmpty()) return
      RepositoryLock.exclusive(repository) { commit(proposal) }
  }
  private void commit(Map proposal) {
    if (proposal.root != repository.directory.path) throw new IllegalArgumentException('Plan belongs to another repository')
    repository.state()
    verify(repository.metadata('state.json'), proposal.stateBefore, 'Cook state')
    proposal.observed.each { path, snapshot ->
      boolean link = snapshot.kind == 'symlink' || proposal.stateAfter.files[path]?.kind == 'symlink'
      verify(repository.target(path, link), snapshot, path, link)
    }
    def nextState = JsonOutput.prettyPrint(JsonOutput.toJson(proposal.stateAfter)) + '\n'
    if (proposal.changes.empty && proposal.stateBefore.content == nextState) return
      files.write(repository.metadata('transaction.json'), JsonOutput.toJson(proposal), null)
    def applied = []
    try {
      proposal.changes.each { change ->
        applied.add(change)
        writeOutput(change, false)
      }
      files.write(repository.metadata('state.json'), nextState, proposal.stateBefore.mode)
      files.write(repository.metadata('transaction.json'), null, null)
    } catch (Exception failure) {
      applied.reverseEach { change -> writeOutput(change, true) }
      files.write(repository.metadata('state.json'), proposal.stateBefore.content, proposal.stateBefore.mode)
      files.write(repository.metadata('transaction.json'), null, null)
      throw failure
    }
  }
  private void writeOutput(Map change, boolean restore) {
    def content = restore ? change.before.content : change.content
    if (change.kind == 'symlink') {
      files.link(repository.target(change.path, true), content)
      return
    }
    files.write(repository.target(change.path), content, restore ? change.before.mode : change.mode)
  }
  private void verify(File file, Map expected, String label, boolean allowLink = false) {
    def actual = repository.snapshot(file, allowLink)
    if (actual.hash != expected.hash || actual.mode != expected.mode || actual.exists != expected.exists || actual.kind != expected.kind)
      throw new IllegalStateException("Changed since preview: ${label}")
  }
  void recover() {
    RepositoryLock.exclusive(repository) {
      def journal = repository.metadata('transaction.json')
      if (!journal.exists()) return
        def proposal = new JsonSlurper().parseText(repository.snapshot(journal).content)
      thecook.reconciliation.PlanSchema.validate(proposal, 'Invalid transaction journal')
      if (proposal.version != 1 || proposal.root != repository.directory.path) throw new IllegalArgumentException('Invalid transaction journal')
      proposal.changes.each { change ->
        boolean link = change.kind == 'symlink'
        def current = repository.snapshot(repository.target(change.path, link), link)
        def afterHash = change.content == null ? null : Repository.digest(change.content.getBytes('UTF-8'))
        boolean beforeMatches = current.hash == change.before.hash && current.mode == change.before.mode && current.kind == change.before.kind
        boolean afterMatches = current.hash == afterHash && current.mode == change.mode &&
            (change.content == null ? !current.exists : current.kind == change.kind)
        if (!beforeMatches && !afterMatches)
          throw new IllegalStateException("Changed after interrupted transaction: ${change.path}")
      }
      def state = repository.snapshot(repository.metadata('state.json'))
      def afterState = JsonOutput.prettyPrint(JsonOutput.toJson(proposal.stateAfter)) + '\n'
      boolean originalState = state.hash == proposal.stateBefore.hash && state.mode == proposal.stateBefore.mode
      boolean writtenState = state.hash == Repository.digest(afterState.getBytes('UTF-8')) && state.mode == (proposal.stateBefore.mode ?: 'rw-------')
      if (!originalState && !writtenState)
        throw new IllegalStateException('Changed after interrupted transaction: Cook state')
      proposal.changes.reverseEach { change -> writeOutput(change, true) }
      files.write(repository.metadata('state.json'), proposal.stateBefore.content, proposal.stateBefore.mode)
      files.write(journal, null, null)
    }
  }
}
