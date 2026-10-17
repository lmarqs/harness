package thecook.cookbook

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

/**
 * The unified diff of one file's change, with three lines of context, as git writes it. The same text reverses the
 * change against the file's current content, wherever later edits moved its hunks.
 */
final class FilePatch {
  private static final int CONTEXT = 3

  private FilePatch() {
  }

  /** The diff from `before` to `after` for `path`; an absent side, `null`, is `/dev/null`. */
  static List<String> of(String path, String before, String after) {
    def original = lines(before)
    def revised = lines(after)
    def diff = UnifiedDiffUtils.generateUnifiedDiff(before == null ? '/dev/null' : "a/${path}",
        after == null ? '/dev/null' : "b/${path}", original, DiffUtils.diff(original, revised), CONTEXT)
    // java-diff-utils numbers an empty side from 1; git and GNU diff write it as 0,0.
    diff.collect { it.startsWith('@@') ? it.replace(' -1,0 ', ' -0,0 ').replace(' +1,0 ', ' +0,0 ') : it }
  }

  /** The lines of `text`, without the newline that ends its last line; none for `null`. */
  static List<String> lines(String text) {
    text == null ? [] : (text.endsWith('\n') ? text.substring(0, text.length() - 1) : text).split('\n', -1).toList()
  }

  /**
   * `current` with the change of the diff `patch` undone, or, when a later edit overlaps the change, the hunks of
   * `patch` that no longer match. The lines the change wrote must be found as it wrote them; the context around them
   * only places them, so an edit next to the change, but not in it, survives.
   */
  static Reversal reverse(List<String> patch, List<String> current) {
    def hunks = hunks(patch)
    List<List<String>> conflicts = []
    List<Map> placed = []
    int shift = 0
    int end = 0
    hunks.each { hunk ->
      runs(hunk).each { run ->
        Integer at = place(run, current, run.expected + shift, end)
        if (at == null) {
          if (!conflicts.contains(hunk)) {
            conflicts.add(hunk)
          }
          return
        }
        placed.add([run: run, at: at])
        shift = at - run.expected
        end = at + run.lines.count { !it.startsWith('-') }
      }
    }
    if (conflicts) {
      return new Reversal(null, conflicts)
    }
    List<String> restored = []
    int next = 0
    placed.each { found ->
      restored.addAll(current.subList(next, found.at))
      next = found.at
      found.run.lines.each { line ->
        if (line.startsWith('-')) {
          restored.add(line.substring(1))
        } else {
          if (line.startsWith(' ')) {
            restored.add(current[next])
          }
          next++
        }
      }
    }
    restored.addAll(current.subList(next, current.size()))
    new Reversal(restored, [])
  }

  // The first line, at or after `from`, where the run's written lines stand as written: the one whose context matches
  // best, then the nearest to `expected`. A run that wrote no line is placed by its context alone, all of which must
  // match.
  private static Integer place(Map run, List<String> current, int expected, int from) {
    List<String> after = run.lines.findAll { !it.startsWith('-') }
    boolean wrote = after.any { it.startsWith('+') }
    if (!wrote && after.isEmpty()) {
      return current.isEmpty() ? 0 : null
    }
    def last = current.size() - after.size()
    if (last < from) {
      return null
    }
    def candidates = (from..last).findAll { at ->
      (0..<after.size()).every { i -> !after[i].startsWith('+') || current[at + i] == after[i].substring(1) }
    }.collect { at -> [at: at, context: (0..<after.size()).count { i -> after[i].startsWith(' ') && current[at + i] == after[i].substring(1) }] }
    def context = after.count { it.startsWith(' ') }
    def fitting = wrote ? candidates : candidates.findAll { it.context == context }
    fitting ? fitting.min { a, b -> b.context <=> a.context ?: (a.at - expected).abs() <=> (b.at - expected).abs() }.at : null
  }

  // Each run of changed lines in the hunk with the context lines around it, and the line where it starts in the
  // content the change left.
  private static List<Map> runs(List<String> hunk) {
    def start = (hunk.first() =~ /\+(\d+)/)[0][1] as int
    def lines = hunk.drop(1)
    List<Map> runs = []
    int i = 0
    int taken = 0
    while (i < lines.size()) {
      if (lines[i].startsWith(' ')) {
        i++
        continue
      }
      int first = i
      while (i < lines.size() && !lines[i].startsWith(' ')) {
        i++
      }
      // Context between two runs goes to the first, so no line belongs to both.
      int from = Math.max(taken, first - CONTEXT)
      int to = i
      while (to < Math.min(lines.size(), i + CONTEXT) && lines[to].startsWith(' ')) {
        to++
      }
      taken = to
      def run = lines.subList(from, to)
      int expected = Math.max(start - 1, 0) + lines.subList(0, from).count { !it.startsWith('-') }
      runs.add([lines: run, expected: expected])
    }
    runs
  }

  private static List<List<String>> hunks(List<String> patch) {
    List<List<String>> hunks = []
    patch.each { line ->
      if (line.startsWith('@@')) {
        hunks.add([line])
      } else if (hunks) {
        hunks.last().add(line)
      }
    }
    hunks
  }

  /** The restored lines, or the hunks that a later edit overlaps. */
  static final class Reversal {
    /** The lines with the change undone; `null` when a hunk conflicts. */
    final List<String> lines
    /** Each hunk, its header and lines, that no longer matches the current content. */
    final List<List<String>> conflicts

    Reversal(List<String> lines, List<List<String>> conflicts) {
      this.lines = lines
      this.conflicts = conflicts
    }
  }
}
