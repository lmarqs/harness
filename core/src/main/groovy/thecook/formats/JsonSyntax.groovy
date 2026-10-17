package thecook.formats

import groovy.json.JsonSlurper

// A source index, not a serializer: ranges let edits leave unrelated bytes untouched.
class JsonSyntax {
  final String text
  int cursor = 0
  JsonSyntax(String text) {
    this.text = text
  }
  Map parse() {
    def root = value()
    whitespace()
    if (cursor != text.length() || root.kind != 'object') {
      fail('Expected one root object')
    }
    root
  }
  private Map value() {
    whitespace(); int start = cursor
    if (cursor >= text.length()) {
      fail('Missing value')
    }
    if (text.charAt(cursor) == '{' as char) {
      return object()
    }
    if (text.charAt(cursor) == '[' as char) {
      return array()
    }
    if (text.charAt(cursor) == '"' as char) {
      string(); return [kind: 'scalar', start: start, end: cursor]
    }
    while (cursor < text.length() && !(text.charAt(cursor).toString() ==~ /[\s,}\]]/)) {
      cursor++
    }
    def token = text.substring(start, cursor)
    if (!(token in ['true', 'false', 'null']) && !(token ==~ /-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?/)) {
      fail('Invalid value')
    }
    [kind: 'scalar', start: start, end: cursor]
  }
  private Map object() {
    int start = cursor++; def members = [:]; whitespace()
    if (peek('}')) {
      cursor++; return [kind: 'object', start: start, end: cursor, members: members]
    }
    while (true) {
      whitespace(); int keyStart = cursor; def key = string()
      if (members.containsKey(key)) {
        fail("Duplicate JSON key: ${key}")
      }
      consume(':'); def node = value()
      members[key] = [keyStart: keyStart, value: node]
      whitespace()
      if (peek('}')) {
        cursor++; break
      }
      consume(',')
    }
    [kind: 'object', start: start, end: cursor, members: members]
  }
  private Map array() {
    int start = cursor++; whitespace()
    if (peek(']')) {
      cursor++; return [kind: 'array', start: start, end: cursor]
    }
    while (true) {
      value()
      whitespace()
      if (peek(']')) {
        cursor++
        break
      }
      consume(',')
    }
    [kind: 'array', start: start, end: cursor]
  }
  private String string() {
    int start = cursor; consume('"')
    boolean escaped = false
    while (cursor < text.length()) {
      def character = text.charAt(cursor++)
      if (character == '"' as char && !escaped) {
        try {
          return new JsonSlurper().parseText(text.substring(start, cursor)).toString()
        }
        catch (Exception error) {
          fail('Invalid JSON string')
        }
      }
      if (character < 32) {
        fail('Control character in JSON string')
      }
      escaped = character == '\\' as char && !escaped
    }
    fail('Unterminated JSON string')
  }
  private void whitespace() {
    while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
      cursor++
    }
  }
  private boolean peek(String character) {
    cursor < text.length() && text.charAt(cursor).toString() == character
  }
  private void consume(String character) {
    whitespace()
    if (!peek(character)) {
      fail("Expected ${character}")
    }
    cursor++
  }
  private void fail(String message) {
    throw new IllegalArgumentException("JSON: ${message} at ${cursor}")
  }
}
