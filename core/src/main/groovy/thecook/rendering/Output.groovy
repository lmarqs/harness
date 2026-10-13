package thecook.rendering

import thecook.composition.ComponentId
import thecook.composition.ContributionIdentity
import thecook.filesystem.TargetPath

class Output {
  final ComponentId owner
  final Map declaration

  Output(ComponentId owner, String path, Map options) {
    this.owner = owner
    if (options.seed && (options.owns || options.format || options.adopt || options.mode))
      throw new IllegalArgumentException('Seed output cannot declare managed ownership, format, adoption or mode')
    declaration = [path: path, kind: options.seed ? 'seed' : (options.format ?: 'blocks'), owns: options.owns ?: false,
      mode: options.mode, adopt: options.adopt ?: false, regions: [:]]
  }
  void configure(Closure body) {
    def configured = body.rehydrate(this, body.owner, body.thisObject)
    configured.resolveStrategy = Closure.DELEGATE_FIRST
    configured.call()
  }
  void block(String key, Closure content) {
    if (!(key ==~ /[a-zA-Z0-9][a-zA-Z0-9_.:-]*/)) throw new IllegalArgumentException("Invalid region identity: ${key}")
    add(key, content)
  }
  void view(Closure renderer) {
    declaration.renderer = renderer
  }
  void text(Closure content) {
    if (!declaration.owns && declaration.kind != 'seed') throw new IllegalArgumentException('Whole-file output requires owns: true')
    if (declaration.kind != 'seed') declaration.kind = 'whole'
    add('content', content)
  }
  void symlink(Closure target) {
    if (!declaration.owns) throw new IllegalArgumentException('Symlink output requires owns: true')
    declaration.kind = 'symlink'
    add('content', target)
  }
  void key(List<String> path, Map options = [:], Closure content) {
    if (path.empty || path.any { !it || it.contains('\n') }) throw new IllegalArgumentException('Invalid key path')
    def identity = new KeyLocation(path).identity(owner).region
    add(identity, content, [path: path.toList(), adopt: options.adopt ?: false, whenPresent: options.whenPresent ?: false])
  }
  private void add(String key, Closure content, Map options = [:]) {
    if (declaration.regions.containsKey(key)) throw new IllegalArgumentException("Duplicate region: ${owner}/${key}")
    declaration.regions[key] = [supplier: content] + options
  }
  RenderedFile render(String provenance = null) {
    def definitions = declaration.regions
    def effective = declaration
    if (declaration.renderer) {
      def view = new Output(owner, declaration.path, declaration + [format: declaration.kind])
      view.declaration.regions.putAll(declaration.regions)
      view.configure(declaration.renderer)
      definitions = view.declaration.regions
      effective = view.declaration
    }
    def kind = FileKind.parse(effective.kind)
    def path = TargetPath.of(effective.path)
    if (kind == FileKind.SEED) {
      if (definitions.keySet() != ['content'] as Set) throw new IllegalArgumentException('Seed output requires one text declaration')
      return new SeedFile(path, definitions.content.supplier.call().toString())
    }
    if (kind.exclusive) {
      def value = definitions.content.supplier.call()
      if (kind == FileKind.SYMLINK && value == null) throw new IllegalArgumentException('Invalid symlink target')
      return new ExclusiveFile(path, kind, owner, value.toString(), effective.mode, effective.adopt, provenance)
    }
    def regions = definitions.collect { key, definition ->
      def location = kind == FileKind.BLOCKS ? new BlockLocation() : new KeyLocation(definition.path)
      new RenderedRegion(ContributionIdentity.of(this.owner.value, key.toString()), location,
          definition.supplier.call(), definition.adopt ?: false, definition.whenPresent ?: false, provenance)
    }
    new PartialFile(path, kind, regions)
  }
}
