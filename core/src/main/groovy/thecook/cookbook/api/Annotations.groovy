// The author's API: the only engine types a recipe or model reaches. The engine imports this package into every unit,
// so recipes write `@param` and models write `@Section` without an import. Recipe annotations are lower case so they
// read as keywords in a recipe; model annotations name the part of a file they declare.

package thecook.cookbook.api

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/** Marks a model class. The engine gives it `directory`, `resource`, `resources` and `current`. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface Ingredient {
}

/** The marked section `id` of a shared text file; `comment` names the line comment when the extension does not. */
@Target([ElementType.METHOD, ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface Section {
  String path()
  String id()
  String comment() default ''
}

/** One key path in a JSON, TOML or YAML document, owned whole. */
@Target([ElementType.METHOD, ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface Key {
  String path()
  String[] segments()
}

/** One owned key per map entry, under the key path `under`; a `List` map key is a deeper path. */
@Target([ElementType.METHOD, ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface Keys {
  String path()
  String[] under() default []
}

/** The whole file; `mode` sets permissions such as `0755`. */
@Target([ElementType.METHOD, ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface File {
  String path()
  String mode() default ''
}

/** One whole file per map entry, under `path`; `mode` sets permissions such as `0755`. */
@Target([ElementType.METHOD, ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface Files {
  String path()
  String mode() default ''
}

/** One symbolic link per map entry, under `path`, pointing at the value. */
@Target([ElementType.METHOD, ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface Links {
  String path()
}

/**
 * Content written only when the file is absent, and never owned. With `existing = "fail"`, cooking the recipe the first
 * time fails when the file already exists, so the file always starts from this content, unless Cook runs with
 * `--force`, which replaces the file, or `--skip`, which keeps it; `"keep"` leaves an existing file.
 */
@Target([ElementType.METHOD, ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface Seed {
  String path()
  String existing() default 'keep'
}

/** Deletes a file no recipe owns, when the member returns true. */
@Target([ElementType.METHOD, ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface Delete {
  String path()
}

/**
 * The cookbook release, `group:version`, whose artifacts a recipe path imports models from; once per cookbook, on one
 * of its imports.
 */
@Retention(RetentionPolicy.RUNTIME)
@interface cookbook {
  String value()
}

/** The recipe's description, in Markdown, on its first import. */
@Retention(RetentionPolicy.RUNTIME)
@interface help {
  String value()
}

/** A consequence the diff cannot show, on the recipe's first import. */
@Retention(RetentionPolicy.RUNTIME)
@interface confirm {
  String value()
}

/** What to do after the writes succeed, on the recipe's first import. */
@Retention(RetentionPolicy.RUNTIME)
@interface yield {
  String value()
}

/** An input bound from `--name=value`; required without a `default`, repeatable with `many`. */
@Target([ElementType.FIELD, ElementType.LOCAL_VARIABLE])
@Retention(RetentionPolicy.RUNTIME)
@interface param {
  String[] value()
  String doc()
  String[] 'default'() default []
  String[] choices() default []
  boolean many() default false
}

/** An injected model instance, at the destination root or at the directory `at`. */
@Target([ElementType.FIELD, ElementType.LOCAL_VARIABLE])
@Retention(RetentionPolicy.RUNTIME)
@interface ingredient {
  Class value()
  String at() default '.'
}
