package thecook.cookbook

import org.tomlj.Toml
import thecook.filesystem.Repository

/**
 * A git repository holding a cookbook, released by tag: `v<version>` when `cookbook.toml` is at its root, or
 * `<dir>/v<version>` for a cookbook in the directory `dir`. The machine's `git` fetches each tag, with the credentials
 * git already has, into a bare repository in the cache, which keeps it for use offline.
 */
final class GitRepository implements CookbookRepository {
  final String url
  /** The cookbook's directory in the repository, or `null` when it is at the root. */
  final String dir
  private final File mirror
  private final Map<String, String> commits = [:]

  GitRepository(String url, String dir, File cache) {
    this.url = url
    this.dir = dir
    this.mirror = new File(cache, Repository.digest(url.getBytes('UTF-8')))
  }

  /** The artifact at the release tag, or `null` when the repository has no such tag or holds another group. */
  CookbookArtifact artifact(Coordinates coordinates) {
    def tag = "${prefix}v${coordinates.version}"
    def commit = commits.computeIfAbsent(tag) { fetch(coordinates, tag) }
    if (commit == null) {
      return null
    }
    def manifest = run('show', "${commit}:${prefix}cookbook.toml")
    if (manifest.exit != 0) {
      throw new IllegalArgumentException("the tag ${tag} in ${url} has no ${prefix}cookbook.toml; " +
      "set dir to the cookbook's directory in cook.toml")
    }
    def toml = Toml.parse(new String(manifest.output, 'UTF-8'))
    if (toml.hasErrors()) {
      throw new IllegalArgumentException("Cannot read ${prefix}cookbook.toml at the tag ${tag} in ${url}: ${toml.errors().first()}")
    }
    if (toml.getString('group') != coordinates.group) {
      return null
    }
    if (toml.getString('version') != coordinates.version) {
      throw new IllegalArgumentException("the tag ${tag} in ${url} holds version ${toml.getString('version')} in " +
      "${prefix}cookbook.toml; tag each release with its own version")
    }
    def directory = "${prefix}${coordinates.artifact}/"
    if (!git('ls-tree', commit, '--', directory).length) {
      return null
    }
    CookbookArtifact.fromZip(coordinates, git('archive', '--format=zip', commit, '--', directory), directory)
  }

  @Override String toString() {
    dir ? "${url} (dir ${dir})" : url
  }

  private String getPrefix() {
    dir ? "${dir}/" : ''
  }

  // A tag the remote lacks means another repository may hold the release; an unreachable remote falls back to the
  // tag fetched before, so a moved tag is fetched again whenever the remote can be read.
  private String fetch(Coordinates coordinates, String tag) {
    if (!mirror.isDirectory()) {
      mirror.parentFile.mkdirs()
      git('init', '--bare', '-q')
    }
    def remote = run('ls-remote', '--exit-code', url, "refs/tags/${tag}")
    if (remote.exit == 2) {
      return null
    }
    if (remote.exit == 0) {
      git('fetch', '-q', '--depth', '1', '--no-tags', url, "+refs/tags/${tag}:refs/tags/${tag}")
    } else if (run('rev-parse', '-q', '--verify', "refs/tags/${tag}").exit != 0) {
      throw new IllegalArgumentException("${coordinates.artifactAddress} is not cached, and ${url} cannot be read: " +
      "${remote.error}; connect to it and run again")
    }
    new String(git('rev-parse', "refs/tags/${tag}^{commit}"), 'UTF-8').trim()
  }

  private byte[] git(Object... arguments) {
    def result = run(arguments)
    if (result.exit != 0) {
      throw new IllegalArgumentException("git ${arguments.first()} failed for ${url}: ${result.error}")
    }
    result.output
  }

  // An explicit --git-dir outranks an inherited GIT_DIR, so a run from a git hook never touches the user's repository.
  private GitResult run(Object... arguments) {
    Process process
    try {
      process = new ProcessBuilder((['git', "--git-dir=${mirror}"] + arguments.toList())*.toString()).start()
    } catch (IOException error) {
      throw new IllegalArgumentException("Cannot run git to read ${url}: ${error.message}; install git and put it on the PATH",
      error)
    }
    def output = new ByteArrayOutputStream()
    def error = new ByteArrayOutputStream()
    process.waitForProcessOutput(output, error)
    new GitResult(process.waitFor(), output.toByteArray(), error.toString('UTF-8').readLines().find { it.trim() }?.trim() ?: '')
  }

  private static final class GitResult {
    final int exit
    final byte[] output
    /** The first line git wrote to stderr, which states the failure. */
    final String error

    GitResult(int exit, byte[] output, String error) {
      this.exit = exit
      this.output = output
      this.error = error
    }
  }
}
