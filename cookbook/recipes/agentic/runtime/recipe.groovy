import thecook.cookbook.ingredients.ralphtui.RalphConfig
import thecook.cookbook.recipes.AgenticRuntime
import thecook.groovy.BatchAdapter

return { kitchen, book, Map options ->
  RalphConfig config = new RalphConfig()
  if (options.agent) config.agent = options.agent.toString()
  if (options.tracker) config.tracker = options.tracker.toString()
  if (options.mode) config.mode = options.mode.toString()
  if (options.prdPath) config.prdPath = options.prdPath.toString()
  if (options.workers != null) config.workers = options.workers.toString().toInteger()
  if (options.maxIterations != null) config.maxIterations = options.maxIterations.toString().toInteger()
  config.adopt = options.adopt?.toString()?.toBoolean() ?: false
  config.autoCommit = options.autoCommit?.toString()?.toBoolean() ?: false
  AgenticRuntime recipe = new AgenticRuntime(config, options.enabled?.toString() != 'false')
  BatchAdapter.mount(kitchen, recipe.compose())
}
