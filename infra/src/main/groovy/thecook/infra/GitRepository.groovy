package thecook.infra

import org.tomlj.Toml
import thecook.cookbook.Coordinates
import thecook.cookbook.CookbookArtifact
import thecook.cookbook.CookbookRepository
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

    if (!commits.containsKey(tag)) {
      commits[tag] = fetch(coordinates, tag)
    }

    def commit = commits[tag]

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

    def files = files(commit, "${prefix}${coordinates.artifact}/")
    files ? new CookbookArtifact(coordinates, files) : null
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
    def ref = "refs/tags/${tag}"

    if (!mirror.isDirectory()) {
      mirror.parentFile.mkdirs()
      git('init', '--bare', '-q')
    }

    // `--` keeps a URL that starts with a dash from being read as an option, such as --upload-pack=<command>.
    def remote = run('ls-remote', '--exit-code', '--', url, ref)

    if (remote.exit == 2) {
      return null
    }

    def cached = run('rev-parse', '-q', '--verify', ref)

    if (remote.exit == 0) {
      // ls-remote matches the pattern against the end of each ref name, so only the line naming the tag itself counts.
      def published = new String(remote.output, 'UTF-8').readLines()*.split('\t').find { it.size() == 2 && it[1] == ref }

      if (published == null) {
        return null
      }

      if (cached.exit != 0 || new String(cached.output, 'UTF-8').trim() != published[0]) {
        git('fetch', '-q', '--depth', '1', '--no-tags', '--', url, "+${ref}:${ref}")
      }
    } else if (cached.exit != 0) {
      throw new IllegalArgumentException("${coordinates.artifactAddress} is not cached, and ${url} cannot be read: " +
      "${remote.error}; connect to it and run again")
    }

    new String(git('rev-parse', "${ref}^{commit}"), 'UTF-8').trim()
  }

  // The blobs as committed, keyed by their path below `directory`. git archive would apply the machine's
  // core.autocrlf and the tag's export attributes, so the sources and their hash would differ between machines.
  private Map<String, byte[]> files(String commit, String directory) {
    def blobs = new String(git('ls-tree', '-r', '-z', commit, '--', directory), 'UTF-8').split('\u0000')
        .findAll { it }.collect { it.split('\t', 2) }.findAll { it[0].split(' ')[1] == 'blob' }
    blobs.collectEntries { blob -> [blob[1].substring(directory.length()), git('cat-file', 'blob', blob[0].split(' ')[2])] }
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
    new GitResult(process.waitFor(), output.toByteArray(), firstError(error.toString('UTF-8')))
  }

  // ssh and git can write warnings and hints before the line that states the failure.
  private static String firstError(String stderr) {
    def lines = stderr.readLines()*.trim().findAll { it }
    lines.find { !(it ==~ /(?i)(warning|hint):.*/) } ?: lines.find() ?: ''
  }

  private static final class GitResult {
    final int exit
    final byte[] output
    /** The first line git wrote to stderr that states the failure, past any warnings and hints. */
    final String error

    GitResult(int exit, byte[] output, String error) {
      this.exit = exit
      this.output = output
      this.error = error
    }
  }
}
