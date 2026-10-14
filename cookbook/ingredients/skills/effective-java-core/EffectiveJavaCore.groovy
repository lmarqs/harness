@Ingredient
class EffectiveJavaCore implements Skill {
  final String name = 'effective-java-core'

  Map<String, String> getFiles() { ['SKILL.md': resource('SKILL.md')] }
}
