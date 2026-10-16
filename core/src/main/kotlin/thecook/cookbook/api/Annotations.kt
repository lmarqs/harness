@file:Suppress("ktlint:standard:class-naming")

// The author's API: the only engine types a recipe or model reaches. The engine imports this package into every
// unit, so recipes write `@param` and models write `@Section` without an import. Recipe annotations are lower case
// so they read as keywords in a recipe; model annotations name the part of a file they declare.

package thecook.cookbook.api

import kotlin.reflect.KClass

/** Marks a model class. The engine gives it `directory`, `resource`, `resources` and `current`. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Ingredient

/** The marked section `id` of a shared text file; `comment` names the line comment when the extension does not. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Section(
  val path: String,
  val id: String,
  val comment: String = "",
)

/** One key path in a JSON, TOML or YAML document, owned whole. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Key(
  val path: String,
  val segments: Array<String>,
)

/** One owned key per map entry, under the key path `under`; a `List` map key is a deeper path. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Keys(
  val path: String,
  val under: Array<String> = [],
)

/** The whole file; `mode` sets permissions such as `0755`. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class File(
  val path: String,
  val mode: String = "",
)

/** One whole file per map entry, under `path`; `mode` sets permissions such as `0755`. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Files(
  val path: String,
  val mode: String = "",
)

/** One symbolic link per map entry, under `path`, pointing at the value. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Links(
  val path: String,
)

/**
 * Content written only when the file is absent, and never owned. With `existing = "fail"`, the recipe's first apply
 * fails when the file already exists, so the file always starts from this content, unless Cook runs with `--force`,
 * which replaces the file, or `--skip`, which keeps it; `"keep"` leaves an existing file.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Seed(
  val path: String,
  val existing: String = "keep",
)

/** Deletes a file no recipe owns, when the member returns true. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Delete(
  val path: String,
)

/** The recipe's description, in Markdown, on its first import. */
@Retention(AnnotationRetention.RUNTIME)
annotation class help(
  val value: String,
)

/** A consequence the diff cannot show, on the recipe's first import. */
@Retention(AnnotationRetention.RUNTIME)
annotation class confirm(
  val value: String,
)

/** What to do after the writes succeed, on the recipe's first import. */
@Retention(AnnotationRetention.RUNTIME)
annotation class `yield`(
  val value: String,
)

/** An input bound from `--name=value`; required without a `default`, repeatable with `many`. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.LOCAL_VARIABLE)
@Retention(AnnotationRetention.RUNTIME)
annotation class param(
  val value: Array<String>,
  val doc: String,
  val default: Array<String> = [],
  val choices: Array<String> = [],
  val many: Boolean = false,
)

/** An injected model instance, at the destination root or at the directory `at`. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.LOCAL_VARIABLE)
@Retention(AnnotationRetention.RUNTIME)
annotation class ingredient(
  val value: KClass<*>,
  val at: String = ".",
)
