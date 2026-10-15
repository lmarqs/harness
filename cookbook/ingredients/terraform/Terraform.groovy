@Ingredient
class Terraform {
  String version
  Map<String, String> files = [:]

  void file(String path, String hcl) { files[path] = hcl }

  Map<String, String> tools() { version ? [terraform: version] : [:] }

  /** Plans the configuration in the directory that holds `path`, with the given environment. */
  Map planCommand(String path, Map<String, String> env = [:]) {
    [run: [['terraform', 'plan']], dir: Path.of(directory, path).normalize().parent?.toString() ?: '.', env: env]
  }

  @Files(path = '.')
  Map<String, String> hclFiles() { files ?: null }
}
