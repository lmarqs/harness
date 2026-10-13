import thecook.cookbook.ingredients.git.Git
import thecook.cookbook.ingredients.thecook.TheCook

return { kitchen, book, Map options ->
  def host = kitchen.recipe('the-cook-setup')
  def cook = new TheCook()
  def git = new Git()
  git.ignore.add(cook.files.local)
  [cook, git].each { contributor ->
    def component = kitchen.ingredient(contributor.id.value)
    component.contribute { contributor }
    host.includes(component)
  }
  host
}
