package thecook.cookbook

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * The superclass the engine gives every `@Ingredient` model that declares none: the instance's `directory` and the
 * reads a model may make, from its own artifact and from the destination as the recipes before it left it.
 */
@CompileStatic
abstract class CookbookModel {
  /** The instance's base inside the destination, with `.` at its root. */
  String directory = '.'

  // Private and read statically: dynamic lookup from a model, in another package, would not find this field.
  private ModelReads engineReads

  /** Places `model` at `directory` with the engine's reads. */
  @PackageScope
  static void attach(CookbookModel model, String directory, ModelReads reads) {
    model.directory = directory
    model.@engineReads = reads
  }

  /** The text of the file `name` packaged in this model's artifact, or `null` when the artifact has none. */
  String resource(String name) {
    this.@engineReads.resource(name)
  }

  /** Every file packaged under `dir` in this model's artifact, keyed by its path below `dir`. */
  Map<String, String> resources(String dir) {
    this.@engineReads.resources(dir)
  }

  /** The destination file at `path` below `directory`, as the recipes before this one left it; `null` when absent. */
  String current(String path) {
    this.@engineReads.current(Declarations.join(directory, path))
  }
}

/** What a model reads through the engine. */
interface ModelReads {
  String resource(String name)

  Map<String, String> resources(String dir)

  String current(String path)
}
