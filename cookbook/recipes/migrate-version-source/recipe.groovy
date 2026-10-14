@param(['runtime'], choices = ['node', 'python'], doc = 'The runtime whose version moves to mise') String runtime
@param(['version'], doc = 'The version mise pins') String version

@ingredient(Mise) def mise
@ingredient(Node) def node
@ingredient(Python) def python

mise.tool(runtime, version)
if (runtime == 'node') node.dropVersionFile()
if (runtime == 'python') python.dropVersionFile()
