package thecook.cookbook

// Lower-case names would read as variables in Groovy expressions.
import thecook.cookbook.api.ingredient as Injected
import thecook.cookbook.api.param as Param
import thecook.cookbook.api.resource as Resource

import java.lang.reflect.Field

/**
 * Runs a compiled recipe: binds its `@param` fields from the arguments, sets its `@resource` fields, injects its
 * ingredients, then runs its body.
 */
final class RecipeRun {
  private RecipeRun() { }

  /**
   * Runs `recipe` with `arguments` and returns its ingredients in declaration order. A bare `--name` is `true` for a
   * yes-or-no param and an error for any other. `resources` holds the text of each `@resource` path.
   * `newIngredient` creates an instance of a model type at a directory.
   */
  static List<Object> run(CompiledRecipe recipe, List<Argument> arguments, Map<String, String> resources,
      Closure newIngredient) {
    def script = (Script) recipe.type.getDeclaredConstructor().newInstance()
    List<Field> fields = recipe.fields.collect { recipe.type.getDeclaredField(it) }
    def params = fields.findAll { it.getAnnotation(Param) }
    def names = params.collectMany {
      it.getAnnotation(Param)
          .value()
          .toList()
    }
    def unknown = arguments*.name.findAll { !(it in names) }

    if (unknown) {
      def takes = names ? 'its arguments are ' + names.collect { "--${it}" }.join(', ') : 'it takes no arguments'
      throw new IllegalArgumentException("Unknown argument --${unknown.first()}; ${takes}")
    }

    params.each { field -> set(script, field, bind(field, arguments, recipe)) }
    fields.findAll { it.getAnnotation(Resource) }.each { field ->
      if (!(field.type in [String, CharSequence, Object])) {
        throw new IllegalArgumentException(
        "${field.name} has the type ${field.type.simpleName}, which a resource cannot have")
      }

      set(script, field, resources[field.getAnnotation(Resource).value()])
    }
    def ingredients = fields.findAll { it.getAnnotation(Injected) }.collect { field ->
      def injected = field.getAnnotation(Injected)
      def instance = newIngredient(injected.value(), injected.at())
      set(script, field, instance)
      instance
    }
    script.run()
    ingredients
  }

  private static Object bind(Field field, List<Argument> arguments, CompiledRecipe recipe) {
    def declared = field.getAnnotation(Param)
    def name = "--${declared.value().first()}"
    def given = arguments.findAll { it.name in declared.value() }
    boolean yesOrNo = field.type in [boolean, Boolean]
    // As in Go's flag package, a yes-or-no value comes only after `=`, so a bare --name means true whatever follows.
    def separate = given.find { it.form == Argument.Form.SEPARATE }

    if (yesOrNo && separate) {
      throw new IllegalArgumentException(
      "--${separate.name} takes no separate value; write --${separate.name}=${separate.value}")
    }

    if (!yesOrNo && given.any { it.form == Argument.Form.BARE }) {
      throw new IllegalArgumentException("${name} needs a value: write ${name}=<value>")
    }

    List<String> values = given.collect { it.form == Argument.Form.BARE ? 'true' : it.value }

    if (!values) {
      if (!recipe.hasDefault(field.name)) {
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
