package thecook

import groovy.json.JsonSlurper
import org.junit.Test
import thecook.formats.Document
import thecook.formats.Documents
import thecook.rendering.KeyLocation

class DocumentsTest {
  @Test void jsonChangesOneNestedValueAndKeepsEveryOtherByte() {
    def original = '{\n "name" : "fixture", "devDependencies" : { "@types/node": "^20.0.0", "other" : "keep" }\n}\n'
    def document = open('json', original)
    set(document, ['devDependencies', '@types/node'], '^22.0.0')
    assert document.text == original.replace('^20.0.0', '^22.0.0')
  }

  @Test void jsonCreatesMissingParentsAndRemovesOnlyTheEmptyOnesItCreated() {
    def document = open('json', '{"name":"fixture","kept":{}}')
    set(document, ['devDependencies', '@types/node'], '^22.0.0')
    set(document, ['kept', 'value'], 1)
    assert new JsonSlurper().parseText(document.text) ==
    [name: 'fixture', kept: [value: 1], devDependencies: ['@types/node': '^22.0.0']]
    remove(document, ['devDependencies', '@types/node'])
    remove(document, ['kept', 'value'])
    assert new JsonSlurper().parseText(document.text) == [name: 'fixture', kept: [:]]
  }

  @Test void jsonKeepsNullsArraysEscapedStringsAndSlashedKeysApart() {
    def document = open('json', '{}')
    set(document, ['a/b'], null)
    set(document, ['a', 'b'], [true, false, 'quote"'])
    assert new JsonSlurper().parseText(document.text) == ['a/b': null, a: [b: [true, false, 'quote"']]]
  }

  @Test void jsonRefusesDuplicateKeys() {
    def error = shouldFail { open('json', '{"x":1,"x":2}') }
    assert error.message.contains('Duplicate JSON key')
  }

  @Test void tomlUpdatesAPinWithoutTouchingCommentsOrOtherKeys() {
    def original = '# Team\n[tools]\nnode = "20" # pin\npython = "3.13"\n\n[env]\nAPP_MODE = "dev"\n'
    def document = open('toml', original)
    set(document, ['tools', 'node'], '22')
    assert document.text == original.replace('node = "20"', 'node = "22"')
  }

  @Test void tomlCreatesAnInlineTableAndRemovesTheTablesItCreated() {
    def original = '# Team\n[env]\nAPP_MODE = "dev"\n'
    def document = open('toml', original)
    set(document, ['tools', 'python'], '3.13')
    set(document, ['env', '_', 'python', 'venv'], [path: '.venv', create: true])
    assert document.text.contains('create = true')
    remove(document, ['env', '_', 'python', 'venv'])
    remove(document, ['tools', 'python'])
    assert document.text == original
  }

  @Test void tomlEditsAnExistingDottedKeyWithoutAddingATable() {
    def document = open('toml', '[env]\n_.python.venv = { path = ".venv", create = false } # keep\n')
    set(document, ['env', '_', 'python', 'venv'], [path: '.venv', create: true])
    assert document.text.contains('} # keep')
    assert document.text.contains('create = true')
    assert !document.text.contains('[env._.python]')
  }

  private static Document open(String format, String text) {
    new Documents().open(format, text)
  }

  private static void set(Document document, List<String> path, Object value) {
    document.write(path.join('.'), new KeyLocation(path), document.render(value))
  }

  private static void remove(Document document, List<String> path) {
    document.remove(path.join('.'), new KeyLocation(path))
  }

  private static Throwable shouldFail(Closure action) {
    try {
      action()
    } catch (IllegalArgumentException error) {
      return error
    }
    throw new AssertionError('Expected an IllegalArgumentException')
  }
}
