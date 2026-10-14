@Ingredient
class Beads {
  boolean tracked = false
  String issuePrefix
  final List<String> localFiles = ['/.beads/dolt/', '/.beads/*.db*']
  final List<String> ephemeralFiles = ['/.beads/*.lock', '/.beads/daemon.log']

  void track(String issuePrefix = null) {
    tracked = true
    this.issuePrefix = issuePrefix
  }

  String readyScript() { resource('ready.sh') }

  @Key(path = '.beads/config.yaml', segments = ['json'])
  Boolean jsonOutput() { tracked ? true : null }

  @Key(path = '.beads/config.yaml', segments = ['issue-prefix'])
  String prefix() { tracked ? issuePrefix : null }
}
