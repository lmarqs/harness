package thecook.cookbook

import groovy.transform.EqualsAndHashCode
import thecook.cookbook.api.Delete
import thecook.cookbook.api.File as WholeFile
import thecook.cookbook.api.Files
import thecook.cookbook.api.Key
import thecook.cookbook.api.Keys
import thecook.cookbook.api.Links
import thecook.cookbook.api.Section
import thecook.cookbook.api.Seed

import java.lang.annotation.Annotation
import java.lang.reflect.AccessibleObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Reads what a model instance declares through its annotated members. */
final class Declarations {
  private static final List<Class<? extends Annotation>> KINDS = [Seed, Section, Key, Keys, WholeFile, Files, Links, Delete]

  private Declarations() { }

  /** `path` below `directory`, the instance base with `.` at the destination root. */
  static String join(String directory, String path) {
    directory in ['.', ''] ? path : "${directory}/${path}"
  }

  /** Everything `instance` declares, member by member in name order; a member returning `null` declares nothing. */
  static List<Declaration> of(Object instance) {
    members(instance.getClass()).collectMany { member ->
      def annotation = KINDS.findResult { member.getAnnotation(it) }
      def value = read(instance, member)
      value == null ? [] : declarations(instance, "${instance.getClass().simpleName}.${name(member)}", annotation, value)
    }
  }

  private static List<AccessibleObject> members(Class type) {
    List<AccessibleObject> found = []
    for (Class current = type; current != null && current != CookbookModel && current != Object; current = current.superclass) {
      def methods = current.declaredMethods.findAll { !it.synthetic && !it.bridge && it.parameterCount == 0 }
      found.addAll((methods + current.declaredFields.toList()).findAll { member ->
        KINDS.any {
          member.getAnnotation(it)
        }
      }
      .sort { name(it) })
    }
    found
  }

  private static String name(AccessibleObject member) {
    member instanceof Method ? "${member.name}()" : member.name
  }

  private static Object read(Object instance, AccessibleObject member) {
    member.accessible = true
    try {
      member instanceof Method ? member.invoke(instance) : member.get(instance)
    } catch (InvocationTargetException error) {
      throw error.cause
    }
  }

  private static List<Declaration> declarations(Object instance, String member, Annotation annotation, Object value) {
    String base = join(instance.directory, expand(instance, member, annotation.path()))
    switch (annotation) {
      case Seed:
        return [new Declaration(member, 'seed', base, null, value.toString())]
      case Section:
        def comment = ((Section) annotation).comment()
        return [new Declaration(member, 'section', base, ((Section) annotation).id(), lines(member, value), null, comment)]
      case Key:
        return [new Declaration(member, 'key', base, ((Key) annotation).segments().toList(), value)]
      case Keys:
        def under = ((Keys) annotation).under().toList()
        return entries(member, value).collect { key, entry ->
          new Declaration(member, 'key', base, under + (key instanceof List ? key*.toString() : [key.toString()]), entry)
        }
      case WholeFile:
        return [new Declaration(member, 'file', base, null, value.toString(), ((WholeFile) annotation).mode())]
      case Files:
        return entries(member, value).collect { name, content ->
          new Declaration(member, 'file', "${base}/${name}", null, content.toString(), ((Files) annotation).mode())
        }
      case Links:
        return entries(member, value).collect { name, target -> new Declaration(member, 'link', "${base}/${name}", null, target.toString()) }
      default:
        return value == true ? [new Declaration(member, 'delete', base, null, null)] : []
    }
  }

  private static String expand(Object instance, String member, String path) {
    path.replaceAll(/\$\{(\w+)}/) { all, property ->
      def value = instance."${property}"
      if (value == null) {
        throw new IllegalArgumentException("${member}: the path ${path} reads ${property}, which is not set")
      }
      value.toString()
    }
  }

  private static List<String> lines(String member, Object value) {
    if (!(value instanceof Collection)) {
      throw new IllegalArgumentException("${member} must return a list of lines")
    }
    value*.toString()
  }

  private static Map entries(String member, Object value) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("${member} must return a map")
    }
    (Map) value
  }
}

/**
 * One part of a destination file a model declares: a `seed`, `section`, `key`, `file`, `link` or `delete`. `address` is
 * a section's region or a key's path; `value` is the section's lines, the key's value, the file's content or the link's
 * target.
 */
final class Declaration {
  final String member
  final String kind
  final String file
  final Object address
  final Object value
  final String mode
  final String comment

  Declaration(String member, String kind, String file, Object address, Object value, String mode = null, String comment = null) {
    this.member = member
    this.kind = kind
    this.file = file
    this.address = address
    this.value = value
    this.mode = mode ?: null
    this.comment = comment ?: null
  }

  /** The target this declaration owns when the recipe `recipe` applies it; `null` for a seed or a delete. */
  Target target(String recipe) {
    switch (kind) {
      case ['seed', 'delete']: return null
      case 'section': return new Target(kind, file, "${recipe}#${address}")
      default: return new Target(kind, file, address)
    }
  }
}

/** A part of a destination file that a recipe owns: a `section`, `key`, `file` or `link`. */
@EqualsAndHashCode
final class Target {
  final String kind
  final String file
  /** The section's marker id, `<recipe>#<region>`; the key's path as a list; or `null` for a whole file or link. */
  final Object address

  Target(String kind, String file, Object address) {
    this.kind = kind
    this.file = file
    this.address = address
  }

  /** Whether writing this target changes `other`: the same section, overlapping keys, or a whole file or link. */
  boolean overlaps(Target other) {
    if (file != other.file) {
      return false
    }
    if (kind in ['file', 'link'] || other.kind in ['file', 'link']) {
      return true
    }
    if (kind != other.kind) {
      return false
    }
    if (kind == 'section') {
      return address == other.address
    }
    List path = (List) address
    List otherPath = (List) other.address
    path.take(otherPath.size()) == otherPath || otherPath.take(path.size()) == path
  }

  @Override
  String toString() {
    switch (kind) {
      case 'section': return "${file} section ${address}"
      case 'key': return "${file} key ${((List) address).join('.')}"
      case 'link': return "${file} link"
      default: return file
    }
  }
}
