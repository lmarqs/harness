package thecook.checks

import org.junit.Test
import thecook.cookbook.Cookbook
import thecook.model.RecipeArguments
import thecook.model.RecipeDeclaration
import thecook.model.RecipeInput

class RecipeArgumentsTest {
  private final RecipeDeclaration declaration = new RecipeDeclaration('declared', [
    new RecipeInput.RequiredPath('policy'),
    new RecipeInput.RequiredChoices('harness', ['codex', 'claude'])
  ])

  @Test void normalizesScalarAndRepeatableValuesWithoutLosingOrder() {
    def values = RecipeArguments.validate(declaration,
        [policy: ['.agents/policy.yaml'], harness: ['claude', 'codex', 'claude']])
    assert values.policy == '.agents/policy.yaml'
    assert values.harness == ['claude', 'codex']
    assert RecipeArguments.validate(declaration,
        [policy: ['.agents/policy.yaml'], harness: ['codex']]).harness == ['codex']
  }

  @Test void optionalChoiceIsAbsentByDefaultAndAcceptsOneSupportedValue() {
    def withAdopt = new RecipeDeclaration('declared', declaration.arguments +
        [new RecipeInput.OptionalChoices('adopt', ['true', 'false'])])
    def base = [policy: ['p'], harness: ['claude']]
    assert !RecipeArguments.validate(withAdopt, base).containsKey('adopt')
    assert RecipeArguments.validate(withAdopt, base + [adopt: ['true']]).adopt == 'true'
    [[adopt: ['yes']], [adopt: ['true', 'false']]].each { invalid ->
      try {
        RecipeArguments.validate(withAdopt, base + invalid)
        assert false: "Accepted ${invalid}"
      } catch (IllegalArgumentException expected) {
        assert expected.message.contains('adopt')
      }
    }
  }

  @Test void rejectsInvalidInputsBeforeTheyReachARecipe() {
    [
      [[policy: ['p'], harness: ['codex'], surprise: ['x']], 'surprise'],
      [[harness: ['codex']], '--policy'],
      [[policy: ['p']], '--harness'],
      [[policy: ['p', 'q'], harness: ['codex']], 'policy'],
      [[policy: ['p'], harness: ['wrong']], 'codex, claude'],
      [[policy: [''], harness: ['codex']], 'policy']
    ].each { entry ->
      try {
        RecipeArguments.validate(declaration, entry[0])
        assert false: "Accepted ${entry[0]}"
      } catch (IllegalArgumentException expected) {
        assert expected.message.contains(entry[1])
      }
    }
  }

  @Test void supplierDeclarationIsStaticallyTypedAndAvailableWithoutExecutingFactory() {
    def root = new File(getClass().getResource('/fixtures/declared-recipe').toURI())
    def book = new Cookbook(new File(root, 'cookbook'))
    def source = new File(root, 'Declaration.groovy')
    book.classes.parseClass(source)
    assert book.declaration('declared').name == 'declared'
    assert book.declaration('declared').arguments*.name == ['policy', 'harness']
  }

  @Test void declarationRejectsReservedAndDuplicateNames() {
    ['repo', 'dry-run', 'remove'].each { name ->
      try {
        new RecipeDeclaration('declared', [new RecipeInput.RequiredPath(name)])
        assert false
      } catch (IllegalArgumentException expected) {
        assert expected.message.contains(name)
      }
    }
    try {
      new RecipeDeclaration('declared', [new RecipeInput.RequiredPath('policy'), new RecipeInput.RequiredPath('policy')])
      assert false
    } catch (IllegalArgumentException expected) {
      assert expected.message.contains('policy')
    }
  }
}
