package thecook.cookbook

class RawRecipeArguments extends LinkedHashMap<String, List<String>> {
  RawRecipeArguments(Map<String, List<String>> values) {
    super(values)
  }
}
