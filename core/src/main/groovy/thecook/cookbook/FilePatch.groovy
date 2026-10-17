package thecook.cookbook

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

/** The unified diff of one file's change, with three lines of context, as git writes it, for the review to show. */
final class FilePatch {
  private static final int CONTEXT = 3
  private static final String NO_NEWLINE = '\\ No newline at end of file'
  // Marks a last line without a newline while diffing, so a change to the final newline alone is a change of lines.
  private static final String UNTERMINATED = '\u0000'

  private FilePatch() {
  }

  /** The diff from `before` to `after` for `path`; an absent side, `null`, is `/dev/null`. */
  static List<String> of(String path, String before, String after) {
    def original = marked(before)
    def revised = marked(after)
    def diff = UnifiedDiffUtils.generateUnifiedDiff(before == null ? '/dev/null' : "a/${path}",
        after == null ? '/dev/null' : "b/${path}", original, DiffUtils.diff(original, revised), CONTEXT)
    diff.collectMany { line ->
      if (line.endsWith(UNTERMINATED)) {
        return [line.substring(0, line.length() - 1), NO_NEWLINE]
      }
      // java-diff-utils numbers an empty side from 1; git and GNU diff write it as 0,0.
      [line.startsWith('@@') ? line.replace(' -1,0 ', ' -0,0 ').replace(' +1,0 ', ' +0,0 ') : line]
    }
  }

  // The lines of `text`, its last line marked when no newline ends it, as git marks it in a diff.
  private static List<String> marked(String text) {
    def lines = lines(text)
    if (text && !text.endsWith('\n')) {
      lines[-1] = lines[-1] + UNTERMINATED
    }
    lines
  }

  // The lines of `text`, without the newline that ends its last line; none for `null`.
  private static List<String> lines(String text) {
    text == null ? [] : (text.endsWith('\n') ? text.substring(0, text.length() - 1) : text).split('\n', -1).toList()
  }
}
