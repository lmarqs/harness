package thecook.checks

import org.junit.Test

class AgentRecipeIdentityTest {
  @Test void instructionRendererRenameKeepsItsHistoricalOwnerAddress() {
    def loader = new GroovyClassLoader(getClass().classLoader)
    Class renderer = loader.parseClass(new File('../cookbook/ingredients/agent-policy/AgentInstructions.groovy'))
    assert renderer.newInstance().id.value == 'agent-policy'
  }

  @Test void rendererInstancesKeepTheirOwnershipNames() {
    def loader = new GroovyClassLoader(getClass().classLoader)
    loader.parseClass(new File('../cookbook/ingredients/agent-policy/Policy.groovy'))
    ['codex': 'Codex', 'claude': 'Claude'].each { name, className ->
      Class target = loader.parseClass(new File("../cookbook/recipes/agentic/${name}/${className}Files.groovy"))
      def policy = loader.loadClass('thecook.cookbook.ingredients.agentpolicy.Policy').newInstance([], [])
      assert target.newInstance(policy).id.value == 'cookbook.' + name
      def custom = target.newInstance(policy, false, 'selected.' + name)
      assert custom.id.value == 'selected.' + name
      assert custom.outputs().every { it.owner == custom.id }
    }
  }
}
