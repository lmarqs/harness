package thecook.cookbook

/**
 * Reverses one recorded batch against the destination's current content, without evaluating its recipes again. Each
 * file's patch is undone where the batch's own lines still stand, so later edits beside them survive. A file the batch
 * created, deleted or turned into or out of a link reverses only when it is still as the batch left it, and
 * permissions the batch changed only while they are as it set them.
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
    def batch = BatchRecord.read(destination, batchId)
    if (batch.result == 'pending') {
      throw new IllegalArgumentException("Batch ${batchId} did not finish writing (pending), so it may have written some of " +
      "its files; ./cook batch inspect ${batchId} shows them and their patches")
    }
    if (batch.result != 'written') {
      throw new IllegalArgumentException("Batch ${batchId} did not finish writing (${batch.result}); there is nothing of it to revert")
    }
    def draft = new Draft(destination)
    Map<String, List<String>> conflicts = [:]
    batch.files.each { file ->
      def conflict = reverse(draft, file, batch.patch(file.path))
      if (conflict) {
        conflicts[file.path] = conflict
      }
    }
    if (conflicts) {
      def listed = conflicts.collectMany { path, lines -> [path] + lines*.replaceAll(/^/, '  ') }
      throw new IllegalArgumentException(
      "Cannot revert ${batchId}: later edits overlap its changes. Resolve these and run it again:\n${listed.join('\n')}")
    }
    Proposal.revert(draft, batchId)
  }

  // Writes the reversal of one file's change into `draft`; returns what conflicts, or nothing.
  private static List<String> reverse(Draft draft, RecordedFile file, List<String> patch) {
    def path = file.path
    def before = file.before
    def after = file.after
    def current = draft.recordedAfter(path)
    if (!file.edit && current != after) {
      return ["changed since the batch ${after == null ? 'deleted it' : 'wrote it'}"]
    }
    if (file.edit && current?.kind != 'file') {
      return ["${current == null ? 'deleted' : 'changed'} since the batch wrote it".toString()]
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
      // The reversal restores the link as one line; any other line is a later edit the link would discard.
      if (reversal.lines.size() != 1) {
        return ['changed since the batch wrote it']
      }
      draft.link(path, (String) before.target)
      return []
    }
    String mode = current?.kind == 'file' ? current.mode : null
    if (file.edit && before.mode != after.mode) {
      if (current.mode != after.mode) {
        return ["permissions changed since the batch set them to ${after.mode}"]
      }
      mode = before.mode
    }
    boolean newline = !file.edit || current.newline == after.newline ? before.newline : current.newline
    draft.write(path, reversal.lines.join('\n') + (newline ? '\n' : ''), mode ?: (String) before.mode)
    []
  }
}
