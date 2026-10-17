package thecook.cookbook

import org.yaml.snakeyaml.Yaml

/**
 * Reverses one recorded batch against the destination's current content, without evaluating its recipes again. Each
 * file's patch is undone where the batch's own lines still stand, so later edits beside them survive. A file the batch
 * created, deleted or turned into or out of a link, or whose permissions it changed, reverses only when it is still
 * as the batch left it.
 */
final class BatchRevert {
  private final File destination

  BatchRevert(File destination) {
    this.destination = destination
  }

  /**
   * The changes that reverse the batch `batchId`, written by `Proposal.commit` as a batch of their own. Fails, naming
   * each conflicting file and hunk, when a later edit overlaps the batch's changes.
   */
  Proposal propose(String batchId) {
    def directory = new File(destination, "${BatchRecord.DIRECTORY}/${batchId}")
    def record = new File(directory, 'batch.yaml')
    if (batchId.contains('/') || !record.isFile()) {
      throw new IllegalArgumentException("No batch ${batchId} is recorded in ${BatchRecord.DIRECTORY}; ./cook batch list shows the recorded batches")
    }
    Map batch = (Map) new Yaml().load(record.getText('UTF-8'))
    if (batch.result != 'written') {
      throw new IllegalArgumentException("Batch ${batchId} did not finish writing (${batch.result}); there is nothing of it to revert")
    }
    def draft = new Draft(destination)
    Map<String, List<String>> conflicts = [:]
    batch.files.each { Map file ->
      def patch = FilePatch.lines(new File(directory, "patches/${file.path}.patch").getText('UTF-8'))
      def conflict = reverse(draft, (String) file.path, (Map) file.before, (Map) file.after, patch)
      if (conflict) {
        conflicts[(String) file.path] = conflict
      }
    }
    if (conflicts) {
      def listed = conflicts.collectMany { path, lines -> [path] + lines*.replaceAll(/^/, '  ') }
      throw new IllegalArgumentException(
      "Cannot revert ${batchId}: later edits overlap its changes. Resolve these and run it again:\n${listed.join('\n')}")
    }
    new Proposal(draft, [], [], new SeedOutcomes(), [:], [command: 'revert', reverts: batchId])
  }

  // Writes the reversal of one file's change into `draft`; returns what conflicts, or nothing.
  private static List<String> reverse(Draft draft, String path, Map before, Map after, List<String> patch) {
    def current = draft.recordedAfter(path)
    boolean whole = before == null || after == null || before.kind != 'file' || after.kind != 'file'
    if (whole && current != after) {
      return ["changed since the batch ${after == null ? 'deleted it' : 'wrote it'}"]
    }
    def reversal = FilePatch.reverse(patch, FilePatch.lines(draft.after(path)))
    if (reversal.conflicts) {
      return reversal.conflicts.flatten() as List<String>
    }
    if (before == null) {
      if (reversal.lines) {
        return ['changed since the batch wrote it']
      }
      draft.delete(path)
      return []
    }
    if (before.kind == 'symlink') {
      draft.link(path, (String) before.target)
      return []
    }
    String mode = current?.kind == 'file' ? current.mode : null
    if (!whole && before.mode != after.mode) {
      if (current.mode != after.mode) {
        return ["permissions changed since the batch set them to ${after.mode}"]
      }
      mode = before.mode
    }
    boolean newline = whole || current.newline == after.newline ? before.newline : current.newline
    draft.write(path, reversal.lines.join('\n') + (newline ? '\n' : ''), mode ?: (String) before.mode)
    []
  }
}
