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
    assert FilePatch.of('notes.md', 'a\nb\n', null) == ['--- a/notes.md', '+++ /dev/null', '@@ -1,2 +0,0 @@', '-a', '-b']
  }

  @Test void showsAChangeToTheFinalNewlineAlone() {
    assert FilePatch.of('n.txt', 'a', 'a\n') == ['--- a/n.txt', '+++ b/n.txt', '@@ -1,1 +1,1 @@', '-a', '\\ No newline at end of file', '+a']
  }
}
