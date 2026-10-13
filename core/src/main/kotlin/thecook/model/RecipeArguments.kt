package thecook.model

sealed interface RecipeInput {
  val name: String

  data class RequiredPath(
    override val name: String,
  ) : RecipeInput

  data class RequiredChoices(
    override val name: String,
    val choices: List<String>,
  ) : RecipeInput {
    init {
      require(choices.isNotEmpty() && choices.all { it.isNotEmpty() } && choices.distinct().size == choices.size) {
        "Invalid choices for recipe argument: $name"
      }
    }
  }

  data class OptionalChoices(
    override val name: String,
    val choices: List<String>,
  ) : RecipeInput {
    init {
      require(choices.isNotEmpty() && choices.all { it.isNotEmpty() } && choices.distinct().size == choices.size) {
        "Invalid choices for recipe argument: $name"
      }
    }
  }
}

class RecipeDeclaration(
  val name: String,
  val arguments: List<RecipeInput>,
) {
  init {
    require(name.matches(Regex("[a-z][a-z0-9-]*(/[a-z][a-z0-9-]*)*"))) { "Invalid recipe identity: $name" }
    val names = mutableSetOf<String>()
    for (argument in arguments) {
      require(argument.name.matches(Regex("[a-z][a-z0-9-]*"))) { "Invalid recipe argument: ${argument.name}" }
      require(argument.name !in RESERVED) { "Reserved recipe argument: ${argument.name}" }
      require(names.add(argument.name)) { "Duplicate recipe argument: ${argument.name}" }
    }
  }

  companion object {
    private val RESERVED = setOf("repo", "dry-run", "remove", "script", "plan", "library", "set", "help")
  }
}

object RecipeArguments {
  @JvmStatic
  fun validate(
    declaration: RecipeDeclaration,
    raw: Map<String, List<String>>,
  ): Map<String, Any> {
    val declared = declaration.arguments.associateBy { it.name }
    val unknown = raw.keys.firstOrNull { it !in declared }
    require(unknown == null) { "Unknown recipe argument: $unknown" }
    val values = linkedMapOf<String, Any>()
    for (argument in declaration.arguments) {
      val supplied = raw[argument.name]
      if (argument is RecipeInput.OptionalChoices && supplied == null) continue
      require(!supplied.isNullOrEmpty()) { "Required argument: --${argument.name}" }
      require(supplied.all { it.isNotEmpty() }) { "Missing value for --${argument.name}" }
      when (argument) {
        is RecipeInput.RequiredPath -> {
          require(supplied.size == 1) { "Argument may only be supplied once: ${argument.name}" }
          values[argument.name] = supplied.single()
        }

        is RecipeInput.RequiredChoices -> {
          val unsupported = supplied.firstOrNull { it !in argument.choices }
          require(unsupported == null) {
            "Unsupported value for --${argument.name}: $unsupported. Allowed choices: ${argument.choices.joinToString(", ")}"
          }
          values[argument.name] = supplied.distinct()
        }

        is RecipeInput.OptionalChoices -> {
          require(supplied.size == 1) { "Argument may only be supplied once: ${argument.name}" }
          require(supplied.single() in argument.choices) {
            "Unsupported value for --${argument.name}: ${supplied.single()}. Allowed choices: ${argument.choices.joinToString(", ")}"
          }
          values[argument.name] = supplied.single()
        }
      }
    }
    return values
  }
}
