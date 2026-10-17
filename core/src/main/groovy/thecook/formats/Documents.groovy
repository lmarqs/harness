package thecook.formats

class Documents {
  final Map<String, Closure<Document>> factories = [blocks: { new BlockDocument(it) }, json: { new JsonDocument(it) },
    toml: { new TomlDocument(it) }, yaml: { new YamlDocument(it) }]
  Document open(String format, String text) {
    def factory = factories[format]

    if (!factory) {
      throw new IllegalArgumentException("Unsupported output format: ${format}")
    }

    factory.call(text)
  }
}
