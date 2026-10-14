@Ingredient
class Terraform {
  String version
  Map<String, String> files = [:]

  void file(String path, String hcl) { files[path] = hcl }

  Map<String, String> tools() { version ? [terraform: version] : [:] }

  String planCommand(String path, Map<String, String> env = [:]) {
    def directory = new File(path).parent ?: '.'
    def prefix = env.collect { name, value -> "${name}=${Mise.quote(value)}" }.join(' ')
    "${prefix} terraform -chdir=${Mise.quote(directory)} plan".trim()
  }

  @Files(path = '.')
  Map<String, String> hclFiles() { files ?: null }
}
