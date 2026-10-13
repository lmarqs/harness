package thecook.reconciliation

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import thecook.filesystem.Repository
import thecook.filesystem.Transaction

class ChangePlan {
  final Repository repository
  final Map proposal

  ChangePlan(Repository repository, Map proposal) {
    this.repository = repository
    this.proposal = new JsonSlurper().parseText(JsonOutput.toJson(proposal))
    PlanSchema.validate(this.proposal)
  }
  List getChanges() {
    proposal.changes
  }
  void save(File file) {
    file.parentFile.mkdirs(); file.text = JsonOutput.prettyPrint(JsonOutput.toJson(proposal)) + '\n'
  }
  static ChangePlan load(Repository repository, File file) {
    def proposal = new JsonSlurper().parseText(file.getText('UTF-8'))
    if (proposal.version != 1 || proposal.root != repository.directory.path)
      throw new IllegalArgumentException('Plan belongs to another repository or version')
    new ChangePlan(repository, proposal)
  }
  void apply() {
    new Transaction(repository).apply(proposal)
  }
}
