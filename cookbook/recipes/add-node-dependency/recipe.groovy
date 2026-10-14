@param(['package'], doc = 'Package name') String packageName
@param(['version'], doc = 'Version range to record') String version
@param(['kind'], choices = ['dependencies', 'devDependencies', 'optionalDependencies', 'peerDependencies'], default = 'dependencies') String kind
@param(['manager'], choices = ['npm', 'pnpm', 'yarn'], default = 'npm') String manager
@param(['dir'], default = '.', doc = 'The package directory') String dir

@ingredient(Node) def node
@ingredient(Mise) def mise

node.directory = dir
node.manager(manager)
node.dependency(packageName, version, kind)
mise.task('dependency:node', node.installCommand())
