package dev.thecook.git

/**
 * Models the ignore patterns Git reads from `.gitignore`.
 *
 * ## Writes for
 *
 * Git ignore syntax in `.gitignore`, with patterns relative to this instance's directory.
 *
 * ## Instances
 *
 * One instance covers one directory. A second `.gitignore` is a second instance `at` another directory,
 * such as `@ingredient(Git, at = 'packages/api')`.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.git.Git
 *
 * @ingredient(Git) def git
 *
 * git.ignore('build/', '*.log')
 * git.ignore(['.cache/', 'tmp/'])
 * ```
 *
 * ## With other models
 *
 * Consumes ignore patterns other models expose, such as `git.ignore(node.ephemeralFiles())`.
 * The recipe passes those patterns to the Git instance for the same directory.
 */
@Ingredient
class Git {
  private List<String> patterns = []

  /** Adds Git ignore patterns as individual strings, relative to this instance's directory. */
  void ignore(String... values) { patterns += values }

  /** Adds a collection of Git ignore patterns, relative to this instance's directory. */
  void ignore(Collection<String> values) { patterns += values }

  /** The `ignore` section of `.gitignore`, with duplicate patterns removed, when patterns are added. */
  @Section(path = '.gitignore', id = 'ignore')
  List<String> ignoreSection() { patterns.unique() ?: null }
}
