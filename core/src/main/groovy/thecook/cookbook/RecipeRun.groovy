package thecook.cookbook

// Lower-case names would read as variables in Groovy expressions.
import thecook.cookbook.api.ingredient as Injected
import thecook.cookbook.api.param as Param

import java.lang.reflect.Field

/** Runs a compiled recipe: binds its `@param` fields from the arguments, injects its ingredients, then runs its body. */
final class RecipeRun {
  private RecipeRun() { }

  /**
   * Runs `recipe` with `arguments`, keyed by param name, and returns its ingredients in declaration order. A `null`
   * value is a bare `--name`: `true` for a yes-or-no param, and an error for any other.
   * `newIngredient` creates an instance of a model type at a directory.
   */
  static List<Object> run(CompiledRecipe recipe, Map<String, List<String>> arguments, Closure newIngredient) {
    def script = (Script) recipe.type.getDeclaredConstructor().newInstance()
    List<Field> fields = recipe.fields.collect { recipe.type.getDeclaredField(it) }
    def params = fields.findAll { it.getAnnotation(Param) }
    def names = params.collectMany { it.getAnnotation(Param).value().toList() }
    def unknown = arguments.keySet().findAll { !(it in names) }
    if (unknown) {
      def takes = names ? 'its arguments are ' + names.collect { "--${it}" }.join(', ') : 'it takes no arguments'
      throw new IllegalArgumentException("Unknown argument --${unknown.first()}; ${takes}")
    }
    params.each { field -> set(script, field, bind(field, arguments, recipe.defaulted.contains(field.name))) }
    def ingredients = fields.findAll { it.getAnnotation(Injected) }.collect { field ->
      def injected = field.getAnnotation(Injected)
      def instance = newIngredient(injected.value(), injected.at())
      set(script, field, instance)
      instance
    }
    script.run()
    ingredients
  }

  private static Object bind(Field field, Map<String, List<String>> arguments, boolean defaulted) {
    def declared = field.getAnnotation(Param)
    def name = "--${declared.value().first()}"
    List<String> values = declared.value().collectMany { arguments[it] ?: [] }
    if (values.contains(null)) {
      if (!(field.type in [boolean, Boolean])) {
        throw new IllegalArgumentException("${name} needs a value: write ${name}=<value>")
      }
      values = values.collect { it == null ? 'true' : it }
    }
    if (!values) {
      if (!defaulted) {
        throw new IllegalArgumentException("Missing ${name}: ${declared.doc()}")
      }
      values = declared."default"().toList()
    }
    if (!declared.many() && values.size() > 1) {
      throw new IllegalArgumentException("${name} takes one value; it was given ${values.size()}")
    }
    def choices = declared.choices().toList()
    values.each { value ->
      if (choices && !(value in choices)) {
        throw new IllegalArgumentException("${name}=${value} is not one of: ${choices.join(', ')}")
      }
    }
    convert(name, field.type, values)
  }

  private static Object convert(String name, Class type, List<String> values) {
    if (type.array) {
      return values as String[]
    }
    if (Collection.isAssignableFrom(type)) {
      return values
    }
    String value = values ? values.first() : null
    if (value == null || type in [String, Object, CharSequence]) {
      return value
    }
    if (type in [boolean, Boolean]) {
      if (!(value in ['true', 'false'])) {
        throw new IllegalArgumentException("${name}=${value} must be true or false")
      }
      return value == 'true'
    }
    if (type in [int, Integer, long, Long]) {
      if (!(value ==~ /-?[0-9]+/)) {
        throw new IllegalArgumentException("${name}=${value} must be a whole number")
      }
      return type in [int, Integer] ? value.toInteger() : value.toLong()
    }
    throw new IllegalArgumentException("${name} has the type ${type.simpleName}, which a param cannot have")
  }

  private static void set(Script script, Field field, Object value) {
    field.accessible = true
    field.set(script, value)
  }
}
