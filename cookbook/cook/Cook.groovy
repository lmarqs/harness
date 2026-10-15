package dev.thecook.cook

/**
 * Models the engine's own footprint in a destination.
 *
 * ## Writes for
 *
 * Supplies Git ignore patterns for the engine's local batch-record directory, `.cook/`.
 * This model declares no writes.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.cook.Cook
 * import dev.thecook.git.Git
 *
 * @ingredient(Cook) def cook
 * @ingredient(Git) def git
 *
 * git.ignore(cook.localFiles)
 * ```
 *
 * ## With other models
 *
 * `localFiles` supplies ignore patterns for `dev.thecook.git.Git`; the recipe passes them to Git.
 */
@Ingredient
class Cook {
  /** Git ignore patterns for the engine's local batch records, rooted in this instance's directory. */
  final List<String> localFiles = ['/.cook/']
}
