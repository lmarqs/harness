package thecook.infra

import org.tomlj.Toml
import org.tomlj.TomlTable
import thecook.cookbook.CookbookRepositories
import thecook.cookbook.CookbookRepository

import java.nio.file.Path

/**
 * The kitchen's `cook.toml`, which lists the repositories cookbook artifacts resolve from, or Maven Central when the
 * kitchen has none. An entry is a Maven-layout repository, an `https://` URL or a path, or a git repository,
 * `{ git = "<url>", dir = "<dir>" }`. A path starting with `~/` is relative to the user's home, any other path to the
 * kitchen.
 */
final class CookToml {
  static final URI CENTRAL = URI.create('https://repo.maven.apache.org/maven2/')

  private CookToml() {
  }

  /**
   * The repositories that `cook.toml` in `kitchen` lists, or Maven Central without that file. Git repositories keep
   * the tags they fetch in `cache`, and `~/` paths resolve against `home`.
   */
  static CookbookRepositories repositories(File kitchen, File cache, File home) {
    def manifest = new File(kitchen, 'cook.toml')
    def central = [new MavenRepository(CENTRAL)] as List<CookbookRepository>

    if (!manifest.isFile()) {
      return new CookbookRepositories(central, "Maven Central; no cook.toml in ${kitchen} lists other repositories")
    }

    def toml = Toml.parse(manifest.toPath())

    if (toml.hasErrors()) {
      throw new IllegalArgumentException("Cannot read ${manifest}: ${toml.errors().first()}")
    }

    if (!toml.contains('repositories')) {
      return new CookbookRepositories(central, "Maven Central; ${manifest} lists no repositories")
    }

    if (!toml.isArray('repositories')) {
      throw misshapen(manifest)
    }

    def array = toml.getArray('repositories')
    List<CookbookRepository> repositories = (0..<array.size()).collect { index ->
      def entry = array.get(index)

      if (entry instanceof String) {
        return maven(entry, kitchen, home, manifest)
      }

      if (entry instanceof TomlTable) {
        return git(entry, kitchen, home, manifest, cache)
      }

      throw misshapen(manifest)
    }
    new CookbookRepositories(repositories, "the repositories in ${manifest}: ${repositories.join(', ')}")
  }

  private static IllegalArgumentException misshapen(File manifest) {
    new IllegalArgumentException("${manifest}: repositories must be a list of https:// URLs, paths and " +
        '{ git = "<url>", dir = "<dir>" } tables')
  }

  private static MavenRepository maven(String entry, File kitchen, File home, File manifest) {
    def (Path directory, String path) = base(entry, kitchen, home)
    def uri = directory.toUri().resolve(path.endsWith('/') ? path : "${path}/")

    if (!(uri.scheme in ['https', 'file'])) {
      throw new IllegalArgumentException("${manifest}: repository ${entry} must be an https:// URL or a path")
    }

    new MavenRepository(uri)
  }

  private static GitRepository git(TomlTable entry, File kitchen, File home, File manifest, File cache) {
    if (!entry.isString('git') || !(entry.keySet() - ['git', 'dir']).isEmpty() || (entry.contains('dir') && !entry.isString('dir'))) {
      throw new IllegalArgumentException("${manifest}: a git repository is { git = \"<url>\", dir = \"<dir>\" }, with dir optional")
    }

    def url = entry.getString('git')

    if (url ==~ /(?i)(http|git):\/\/.*/) {
      throw new IllegalArgumentException("${manifest}: git repository ${url} must use https://, ssh or a path; " +
      'http:// and git:// are not authenticated')
    }

    // As git does, a colon before the first slash makes `host:path` an ssh address rather than a path.
    if (!url.contains('://') && !(url ==~ /[^\/]+:.*/)) {
      def (Path directory, String path) = base(url, kitchen, home)
      url = directory.resolve(path).normalize().toString()
    }

    def dir = entry.getString('dir')?.replaceAll(/\/+$/, '') ?: null

    if (dir != null && (dir.startsWith('/') || '..' in dir.split('/'))) {
      throw new IllegalArgumentException("${manifest}: dir ${dir} must be a directory inside the git repository")
    }

    new GitRepository(url, dir, cache)
  }

  // A path entry's directory and its path from there: the user's home for `~/`, as shells read it, otherwise the kitchen.
  private static Tuple2<Path, String> base(String entry, File kitchen, File home) {
    entry.startsWith('~/')
        ? new Tuple2<>(home.toPath().toAbsolutePath(), entry.substring(2))
        : new Tuple2<>(kitchen.toPath().toAbsolutePath(), entry)
  }
}
