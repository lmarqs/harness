@param(['node'], default = '22', doc = 'Node version') String nodeVersion
@param(['manager'], choices = ['npm', 'yarn'], default = 'npm') String manager
@param(['manager-version'], default = null) String managerVersion

@ingredient(Node) def node
@ingredient(Mise) def mise
@ingredient(Git) def git

node.version = nodeVersion
node.manager(manager)
node.pinManager(managerVersion)

mise.tools(node.tools())
mise.script('node:setup', node.setupScript())
mise.script('node:lock', node.lockScript())
git.ignore(node.ephemeralFiles())
