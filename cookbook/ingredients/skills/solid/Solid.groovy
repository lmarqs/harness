@Ingredient
class Solid implements Skill {
  final String name = 'solid'

  Map<String, String> getFiles() { ['SKILL.md': resource('SKILL.md')] }
}
