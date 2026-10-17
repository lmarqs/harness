package thecook.cookbook

/** One recipe argument as written after its recipe: `--name`, `--name=value` or `--name value`. */
final class Argument {
  /** How the argument was written. */
  enum Form {
    /** `--name`, with no value. */
    BARE,
    /** `--name=value`. */
    INLINE,
    /** `--name value`, the value as the next word. */
    SEPARATE
  }

  final String name
  /** The value, or `null` for a bare `--name`. */
  final String value
  final Form form

  private Argument(String name, String value, Form form) {
    this.name = name
    this.value = value
    this.form = form
  }

  static Argument bare(String name) {
    new Argument(name, null, Form.BARE)
  }

  static Argument inline(String name, String value) {
    new Argument(name, value, Form.INLINE)
  }

  static Argument separate(String name, String value) {
    new Argument(name, value, Form.SEPARATE)
  }

  /** The argument `name` with `value`, as `form` writes it. */
  static Argument of(String name, String value, Form form) {
    new Argument(name, value, form)
  }
}
