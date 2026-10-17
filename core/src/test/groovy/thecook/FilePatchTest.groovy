package thecook

import org.junit.Test
import thecook.cookbook.FilePatch

class FilePatchTest {
  @Test void writesTheDiffAsGitDoes() {
    assert FilePatch.of('settings.toml', 'test = "old"\nother = "x"\n', 'test = "new"\nother = "x"\n') == [
      '--- a/settings.toml',
      '+++ b/settings.toml',
      '@@ -1,2 +1,2 @@',
      '-test = "old"',
      '+test = "new"',
      ' other = "x"',
    ]
    assert FilePatch.of('notes.md', null, 'a\n') == ['--- /dev/null', '+++ b/notes.md', '@@ -0,0 +1,1 @@', '+a']
  }

  @Test void reversesTheChangeAndKeepsALaterEditToAnotherLine() {
    def patch = FilePatch.of('settings.toml', 'test = "old"\nother = "x"\n', 'test = "new"\nother = "x"\n')

    def reversal = FilePatch.reverse(patch, ['test = "new"', 'other = "edited"'])

    assert reversal.conflicts == []
    assert reversal.lines == ['test = "old"', 'other = "edited"']
  }

  @Test void findsAHunkThatLaterLinesMoved() {
    def before = (1..20).collect { "line ${it}".toString() }
    def after = before.collect { it == 'line 15' ? 'line fifteen' : it }
    def patch = FilePatch.of('notes.md', before.join('\n'), after.join('\n'))

    def reversal = FilePatch.reverse(patch, ['added', 'also added'] + after)

    assert reversal.conflicts == []
    assert reversal.lines == ['added', 'also added'] + before
  }

  @Test void namesTheHunksALaterEditOverlapsAndRestoresNothing() {
    def before = (1..20).collect { "line ${it}".toString() }
    def after = before.collect { it in ['line 2', 'line 15'] ? "${it} changed".toString() : it }
    def patch = FilePatch.of('notes.md', before.join('\n'), after.join('\n'))

    def reversal = FilePatch.reverse(patch, after.collect { it == 'line 15 changed' ? 'line 15 edited' : it })

    assert reversal.lines == null
    assert reversal.conflicts == [
      [
        '@@ -12,7 +12,7 @@',
        ' line 12',
        ' line 13',
        ' line 14',
        '-line 15',
        '+line 15 changed',
        ' line 16',
        ' line 17',
        ' line 18',
      ]
    ]
  }

  @Test void placesEachChangeOfAHunkOnItsOwn() {
    def before = ['a', 'b', 'c', 'd', 'e']
    def after = ['a', 'B', 'c', 'D', 'e']
    def patch = FilePatch.of('notes.md', before.join('\n'), after.join('\n'))

    def reversal = FilePatch.reverse(patch, ['a', 'B', 'inserted', 'c', 'D', 'e'])

    assert reversal.lines == ['a', 'b', 'inserted', 'c', 'd', 'e']
  }

  @Test void restoresDeletedLinesOnlyWhereAllTheirContextStands() {
    def patch = FilePatch.of('notes.md', 'a\nb\nc\n', 'a\nc\n')

    assert FilePatch.reverse(patch, ['x', 'a', 'c']).lines == ['x', 'a', 'b', 'c']
    assert FilePatch.reverse(patch, ['a', 'C']).conflicts == [['@@ -1,3 +1,2 @@', ' a', '-b', ' c']]
  }

  @Test void restoresADeletionWhoseContextTheChangeBeforeItHolds() {
    def patch = FilePatch.of('notes.md', 'x\na\nc\nd\n', 'x\nb\nc\n')

    assert FilePatch.reverse(patch, ['x', 'b', 'c']).lines == ['x', 'a', 'c', 'd']
  }

  @Test void restoresLinesDeletedAtEitherEndOfTheFileOnlyThere() {
    def last = FilePatch.of('notes.md', 'a\nb\nc\nx\n', 'a\nb\nc\n')
    def first = FilePatch.of('notes.md', 'x\na\nb\nc\n', 'a\nb\nc\n')

    assert FilePatch.reverse(last, ['a', 'b', 'c']).lines == ['a', 'b', 'c', 'x']
    assert FilePatch.reverse(last, ['a', 'b', 'c', 'x']).conflicts == [['@@ -1,4 +1,3 @@', ' a', ' b', ' c', '-x']]
    assert FilePatch.reverse(first, ['a', 'b', 'c']).lines == ['x', 'a', 'b', 'c']
    assert FilePatch.reverse(first, ['x', 'a', 'b', 'c']).conflicts == [['@@ -1,4 +1,3 @@', '-x', ' a', ' b', ' c']]
  }

  @Test void reversesACreationAndADeletion() {
    assert FilePatch.reverse(FilePatch.of('notes.md', null, 'a\nb\n'), ['a', 'b']).lines == []
    assert FilePatch.reverse(FilePatch.of('notes.md', 'a\nb\n', null), []).lines == ['a', 'b']
  }

  @Test void readsTheLinesOfATextWithOrWithoutItsLastNewline() {
    assert FilePatch.lines(null) == []
    assert FilePatch.lines('a\nb\n') == ['a', 'b']
    assert FilePatch.lines('a\nb') == ['a', 'b']
    assert FilePatch.lines('a\n\n') == ['a', '']
    assert FilePatch.lines('\n') == ['']
  }
}
