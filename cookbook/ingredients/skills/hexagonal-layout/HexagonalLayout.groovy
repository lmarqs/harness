@Ingredient
class HexagonalLayout implements Skill {
  final String name = 'hexagonal-layout'

  Map<String, String> getFiles() { ['SKILL.md': resource('SKILL.md')] }
}
