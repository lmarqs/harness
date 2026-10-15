package dev.thecook.beads

/**
 * Models the files the Beads tracker reads and leaves behind.
 *
 * ## Writes for
 *
 * Beads configuration in `.beads/config.yaml`: the `json` output setting and the optional `issue-prefix`.
 * The packaged ready-task script runs the `bd` CLI with JSON output and requires `bd` on the PATH.
 *
 * ## Instances
 *
 * One instance covers one repository's tracker. A tracker in another directory uses a separate instance
 * `at` that directory, with its own `.beads/config.yaml`.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.beads.Beads
 * import dev.thecook.git.Git
 * import dev.thecook.mise.Mise
 *
 * @ingredient(Beads) def beads
 * @ingredient(Git) def git
 * @ingredient(Mise) def mise
 *
 * beads.track('cook')
 * git.ignore(beads.localFiles + beads.ephemeralFiles)
 * mise.script('beads:ready', beads.readyScript())
 * ```
 *
 * ## With other models
 *
 * `localFiles` and `ephemeralFiles` supply Git ignore patterns. `readyScript()` supplies a mise file-task
 * script that lists ready tasks without changing the tracker. The recipe passes these facts to Git and Mise.
 */
@Ingredient
class Beads {
  private boolean tracked = false
  private String issuePrefix

  /** Git ignore patterns for Beads' local Dolt directory and database files, rooted in this instance's directory. */
  final List<String> localFiles = ['/.beads/dolt/', '/.beads/*.db*']

  /** Git ignore patterns for Beads' transient lock files and daemon log, rooted in this instance's directory. */
  final List<String> ephemeralFiles = ['/.beads/*.lock', '/.beads/daemon.log']

  /**
   * Enables JSON output for this repository's tracker and sets its issue prefix when supplied.
   * A null prefix declares no `issue-prefix` key. A later call replaces the requested prefix.
   */
  void track(String issuePrefix = null) {
    tracked = true
    this.issuePrefix = issuePrefix
  }

  /** The packaged mise file-task script running `bd ready --json`, forwarding all task arguments to `bd`. */
  String readyScript() { resource('ready.sh') }

  /** The `json` key in `.beads/config.yaml`, set to true when `track()` is called; otherwise declares nothing. */
  @Key(path = '.beads/config.yaml', segments = ['json'])
  Boolean jsonOutput() { tracked ? true : null }

  /** The `issue-prefix` key in `.beads/config.yaml`, when `track()` is called with a non-null prefix. */
  @Key(path = '.beads/config.yaml', segments = ['issue-prefix'])
  String prefix() { tracked ? issuePrefix : null }
}
