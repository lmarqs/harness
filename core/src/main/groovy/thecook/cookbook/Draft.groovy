package thecook.cookbook

import thecook.filesystem.AtomicFiles
import thecook.filesystem.Repository
import thecook.formats.Document

/**
 * The destination as the recipes of one invocation leave it, held in memory. Recipes read and write it in order, so
 * each one sees what the ones before it wrote; nothing reaches the disk until `commit`.
 */
final class Draft {
  private static final String DEFAULT_MODE = 'rw-r--r--'

  private final Repository repository
  private final AtomicFiles files = new AtomicFiles()
  private final Map<String, Map> before = [:]
  private final Map<String, Map> after = [:]

  Draft(File destination) {
    repository = new Repository(destination)
  }

  /** The file's text, or a link's target, as the invocation left it so far; `null` when absent. */
  String read(String path) {
    state(path).content
  }

  /** The target of the symbolic link at `path` as the invocation left it so far; `null` when `path` is not a link. */
  String linkTarget(String path) {
    def current = state(path)
    current.kind == 'symlink' ? current.content : null
  }

  /** Writes `content` to `path`; `mode` replaces the file's permissions, which otherwise stay as they are. */
  void write(String path, String content, String mode = null) {
    def current = state(path)
    after[path] = [exists: true, content: content, kind: 'file',
      mode: mode ?: (current.kind == 'file' ? current.mode : null) ?: DEFAULT_MODE]
  }

  /** Writes `document` to `path`, or deletes `path` when the document is empty. */
  void replace(String path, Document document) {
    if (document.empty) {
      delete(path)
    } else {
      write(path, document.text)
    }
  }

  /** Makes `path` a symbolic link to `target`. */
  void link(String path, String target) {
    state(path)
    after[path] = [exists: true, content: target, kind: 'symlink', mode: null]
  }

  /** Deletes `path` when it exists. */
  void delete(String path) {
    state(path)
    after[path] = [exists: false, content: null, kind: null, mode: null]
  }

  /** The paths whose content, kind or permissions differ from the disk, in path order. */
  List<String> getChanges() {
    after.keySet().findAll { changed(it) }.sort()
  }

  /** The text of `path` on the disk, or `symbolic link to <target>` for a link; `null` when absent. */
  String before(String path) {
    shown(before[path])
  }

  /** The text of `path` as the draft leaves it, or `symbolic link to <target>` for a link; `null` when absent. */
  String after(String path) {
    shown(state(path))
  }

  /** What happens to a changed `path`: `create` when it is absent from the disk, `delete` when the draft removes it, else `update`. */
  String action(String path) {
    if (!before[path].exists) {
      return 'create'
    }
    after[path].exists ? 'update' : 'delete'
  }

  /**
   * Writes the changes to the disk, after checking that no file changed since the draft read it. On a failed write, the
   * changes already made are undone.
   */
  void commit() {
    def paths = changes
    if (!paths) {
      return
    }
    paths.each { path ->
      if (repository.snapshot(repository.target(path, true), true).hash != before[path].hash) {
        throw new IllegalStateException("Changed since preview: ${path}")
      }
    }
    List<String> written = []
    try {
      paths.each { path ->
        written.add(path)
        put(path, after[path])
      }
    } catch (Exception failure) {
      written.reverseEach { put(it, before[it]) }
      throw failure
    }
  }

  private Map state(String path) {
    if (!before.containsKey(path)) {
      def snapshot = repository.snapshot(repository.target(path, true), true)
      before[path] = snapshot + [kind: snapshot.exists ? (snapshot.kind ?: 'file') : null]
    }
    after[path] ?: before[path]
  }

  private static String shown(Map state) {
    state.kind == 'symlink' ? "symbolic link to ${state.content}" : state.content
  }

  private boolean changed(String path) {
    def was = before[path]
    def now = after[path]
    was.exists != now.exists || was.content != now.content || was.kind != now.kind || (now.kind == 'file' && was.mode != now.mode)
  }

  private void put(String path, Map state) {
    if (state.kind == 'symlink') {
      files.link(repository.target(path, true), state.content)
    } else {
      // A link at the path is replaced, not followed.
      files.write(repository.target(path, true), state.exists ? state.content : null, state.mode)
    }
    if (!state.exists) {
      def parent = repository.target(path, true).parentFile
      while (parent != repository.directory && parent.list()?.length == 0) {
        parent.delete()
        parent = parent.parentFile
      }
    }
  }
}
