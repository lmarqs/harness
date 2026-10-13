package thecook.rendering

import groovy.transform.CompileStatic
import thecook.composition.Component
import thecook.composition.ContributionIdentity
import thecook.model.Batch

@CompileStatic
class RenderTree {
  final List<RenderedFile> outputs
  final Map<String, List<RenderedFile>> applications
  final Map<String, Map> provenance

  RenderTree(List<RenderedFile> outputs) {
    this(outputs, [:])
  }

  RenderTree(List<RenderedFile> outputs, Map<String, List<RenderedFile>> applications) {
    this(outputs, applications, [:])
  }

  RenderTree(List<RenderedFile> outputs, Map<String, List<RenderedFile>> applications, Map<String, Map> provenance) {
    this.outputs = List.copyOf(outputs)
    this.applications = applications.collectEntries { String id, List<RenderedFile> files -> [(id): List.copyOf(files)] }.asImmutable()
    this.provenance = provenance.asImmutable()
  }

  static RenderTree from(Batch batch) {
    combine(RenderedFile.evaluate(batch.contributors))
  }

  static RenderTree from(List<Component> components) {
    validateDependencies(components)
    List<RenderedFile> outputs = []
    Map<String, List<RenderedFile>> applications = [:]
    Map<String, Map> provenance = [:]
    for (Component component : components) {
      for (Object check : (List) component.definition.checks) component.evaluate((Closure) check)
    }
    for (Component component : components) {
      List<RenderedFile> files = component.renderOutputs()
      outputs.addAll(files)
      String application = (String) component.definition.application
      if (application != null) {
        if (applications.containsKey(application))
          throw new IllegalArgumentException("Duplicate application: ${application}")
        applications[application] = combine(files).outputs
        if (component.definition.provenance) provenance[application] = (Map) component.definition.provenance
      }
    }
    RenderTree combined = combine(outputs)
    new RenderTree(combined.outputs, applications, provenance)
  }

  private static RenderTree combine(List<RenderedFile> outputs) {
    Map<String, List<RenderedFile>> paths = outputs.groupBy { it.path.value.toLowerCase(Locale.ROOT) }
    List<RenderedFile> combined = []
    paths.each { String canonical, List<RenderedFile> files ->
      RenderedFile first = files.first()
      if (files.any { it.path != first.path })
        throw new IllegalArgumentException('Multiple writers with case collision: ' + files.collect { it.path.value }.join(', '))
      if (files.size() > 1 && (first.kind.exclusive || files.any { it.kind.exclusive || it.kind != first.kind }))
        throw new IllegalArgumentException("Multiple writers with incompatible ownership or format: ${first.path}")
      if (first.kind.exclusive) {
        combined.add(first); return
      }
      List<RenderedRegion> regions = files.collectMany { ((PartialFile) it).regions }
      Map<ContributionIdentity, RenderedRegion> seen = [:]
      for (RenderedRegion region : regions) {
        if (seen.containsKey(region.identity)) throw new IllegalArgumentException("Duplicate contribution: ${first.path} ${region.identity}")
        if (region.location instanceof KeyLocation) {
          RenderedRegion conflict = seen.values().find {
            it.location instanceof KeyLocation &&
                ((KeyLocation) region.location).overlaps((KeyLocation) it.location)
          }
          if (conflict) throw new IllegalArgumentException("Overlapping keys: ${first.path}: ${region.identity} conflicts with ${conflict.identity}")
        }
        seen[region.identity] = region
      }
      combined.add(new PartialFile(first.path, first.kind, regions.sort { it.identity.toString() }))
    }
    paths.keySet().each { String path ->
      if (paths.keySet().any { it != path && it.startsWith(path + '/') })
        throw new IllegalArgumentException("File-directory collision: ${path}")
    }
    new RenderTree(combined.sort { it.path.value })
  }

  /** Reporting serialization; never fed back into aggregation or reconciliation. */
  List<Map> describe() {
    List<Map> reports = []
    for (RenderedFile file : outputs) {
      Map<String, Object> report = [path: file.path.value, kind: file.kind.wireName]
      if (file instanceof ExclusiveFile) {
        report.putAll([owner: file.owner.value, mode: file.mode, adopt: file.adopt,
          definitionIdentity: file.definitionIdentity, regions: [content: [value: file.content]]])
      } else {
        report.regions = ((PartialFile) file).regions.collectEntries { RenderedRegion region ->
          Map fields = [value: region.value, owner: region.identity.instance.value, definitionIdentity: region.definitionIdentity]
          if (region.location instanceof KeyLocation) fields.putAll([path: ((KeyLocation) region.location).path,
            adopt: region.adopt, whenPresent: region.whenPresent])
          [(region.identity.toString()): fields]
        }
      }
      reports.add(report)
    }
    reports
  }

  private static void validateDependencies(List<Component> components) {
    Map<Object, Component> slots = [:]
    for (Component component : components) {
      for (Object dependency : (List) component.definition.requires) {
        if (!components.contains(dependency)) throw new IllegalArgumentException("${component.id} requires mounted ${((Component) dependency).id}")
      }
      Object slot = ((Map) component.definition.parameters).get('slot')
      if (slot && slots.containsKey(slot)) throw new IllegalArgumentException("Multiple components in slot ${slot}")
      if (slot) slots[slot] = component
    }
  }
}
