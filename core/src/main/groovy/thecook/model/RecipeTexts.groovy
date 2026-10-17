package thecook.model

import groovy.transform.Immutable

/** A recipe's help as its source declares it, read without running the recipe. */
@Immutable
final class RecipeTexts {
  String help
  String confirm
  String yieldText
  List<RecipeParam> params
  List<String> ingredients
}

/** One `@param` declaration: its names, the field type as written, its `doc`, default, choices and repeatability. */
@Immutable
final class RecipeParam {
  List<String> names
  String type
  String doc
  String defaultValue
  List<String> choices
  boolean many
}
