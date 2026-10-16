package thecook.cookbook

import thecook.filesystem.AtomicFiles
import thecook.filesystem.Repository
import thecook.formats.Document

import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

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

  /**
   * Writes the changes to the disk, after checking that no file changed since the draft read it. On a failed write, the
   * changes already made are undone.
   */
  void commit() {
    def paths = changes
    if (!paths) {
      return
    }
    exclusive {
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
  }

  /** Runs `operation` while no other Cook process writes this destination; the lock file stays out of it. */
  private void exclusive(Closure operation) {
    def name = "the-cook-${Repository.digest(repository.directory.path.getBytes('UTF-8'))}.lock"
    def file = new File(System.getProperty('java.io.tmpdir'), name)
    FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).withCloseable { channel ->
      def held
      try {
        held = channel.tryLock()
      } catch (OverlappingFileLockException ignored) {
        held = null
      }
      if (held == null) {
        throw new IllegalStateException("Another Cook process is writing ${repository.directory}")
      }
      try {
        operation()
      } finally {
        held.release()
      }
    }
  }

  private Map state(String path) {
    if (!before.containsKey(path)) {
      def snapshot = repository.snapshot(repository.target(path, true), true)
      before[path] = snapshot + [kind: snapshot.exists ? (snapshot.kind ?: 'file') : null]
    }
    after[path] ?: before[path]
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
