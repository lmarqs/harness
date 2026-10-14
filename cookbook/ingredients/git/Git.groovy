@Ingredient
class Git {
  List<String> patterns = []

  void ignore(String... values) { patterns += values }

  void ignore(Collection<String> values) { patterns += values }

  @Section(path = '.gitignore', id = 'ignore')
  List<String> ignoreSection() { patterns.unique() ?: null }
}
