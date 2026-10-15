package dev.thecook.go

import java.util.regex.Pattern

/**
 * Models a Go module's requirements and toolchain version.
 *
 * ## Writes for
 *
 * Go's `go.mod` syntax: individual requirement lines in an existing module file. Toolchain pins and commands
 * are supplied as facts for a task runner rather than written by this model.
 *
 * ## Instances
 *
 * One instance covers one module directory. Use `at` for a module in a subdirectory; its `go.mod` must exist
 * when requirements are requested. The tidy and test command runs in that instance's directory.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.go.Go
 * import dev.thecook.mise.Mise
 *
 * @ingredient(Go) def go
 * @ingredient(Mise) def mise
 *
 * go.version = '1.23.4'
 * go.require('github.com/google/uuid', 'v1.6.0')
 * mise.tools(go.tools())
 * mise.task('dependency:go', go.tidyCommand())
 * ```
 *
 * ## With other models
 *
 * `tools()` supplies the Go toolchain pin and `tidyCommand()` supplies the tidy and test command for Mise.
 * The recipe passes these facts to the task runner; Go owns only the requested requirement lines.
 */
@Ingredient
class Go {
  /** The toolchain version exposed by `tools()` for mise; unset, no Go tool pin is supplied. */
  String version
  private Map<String, String> requires = [:]

  /**
   * Requests a module at a literal version. Module paths accept letters, digits, underscores, dots, slashes
   * and hyphens; versions start with `v` and a digit, followed by letters, digits, dots, underscores, pluses
   * or hyphens. Other strings are rejected. A later call for the same module replaces the requested version.
   * Declaration rejects an absent `go.mod` or an existing requirement at a different version.
   */
  void require(String module, String version) {
    if (!(module ==~ /[a-zA-Z0-9_.\/-]+/) || !(version ==~ /v[0-9][a-zA-Z0-9_.+-]*/))
      throw new IllegalArgumentException("Module and version must be literal: ${module} ${version}")
    requires[module] = version
  }

  /** The `go` tool pin for mise when a toolchain version is set; otherwise an empty map. */
  Map<String, String> tools() { version ? [go: version] : [:] }

  /** A command running `go mod tidy`, then `go test ./...`, in this instance's module directory. */
  Map tidyCommand() { [run: [['go', 'mod', 'tidy'], ['go', 'test', './...']], dir: directory] }

  /**
   * One requirement line in `go.mod` per requested module, when `require` is called. Keeps an existing line
   * as written at the requested version, appends a `require` line when absent, and rejects another version
   * or a missing `go.mod`. With no requested modules, declares nothing.
   */
  @Lines(path = 'go.mod')
  Map<String, String> requireLines() {
    if (!requires) return null
    def text = current('go.mod')
    if (text == null) throw new IllegalArgumentException("${directory}/go.mod must exist")
    requires.collectEntries { module, version ->
      def line = ~/^\s*(?:require\s+)?${Pattern.quote(module)}\s+(\S+)/
      def existing = text.readLines().find { it =~ line }
      if (existing && (existing =~ line)[0][1] != version)
        throw new IllegalArgumentException("${module} is required at another version; change the existing declaration")
      [line.pattern(), existing ?: "require ${module} ${version}"]
    }
  }
}
