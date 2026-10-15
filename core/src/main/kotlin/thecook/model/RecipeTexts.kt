package thecook.model

/** A recipe's help as its source declares it, read without running the recipe. */
data class RecipeTexts(
  val help: String,
  val confirm: String?,
  val yieldText: String?,
  val params: List<RecipeParam>,
  val ingredients: List<String>,
)

/** One `@param` declaration: its names, the field type as written, its `doc`, default, choices and repeatability. */
data class RecipeParam(
  val names: List<String>,
  val type: String,
  val doc: String?,
  val default: String?,
  val choices: List<String>,
  val many: Boolean,
)
