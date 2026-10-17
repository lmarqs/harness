package thecook

import org.junit.Test
import thecook.cookbook.Coordinates

class CoordinatesTest {
  @Test void parsesCoordinatesWithAnExactReleaseVersion() {
    def coordinates = Coordinates.parse('dev.thecook:agents:0.1.0:instructions')
    assert [coordinates.group, coordinates.artifact, coordinates.version, coordinates.recipe] ==
    ['dev.thecook', 'agents', '0.1.0', 'instructions']
    assert coordinates.jarPath == 'dev/thecook/agents/0.1.0/agents-0.1.0.jar'
    assert Coordinates.parse('dev.thecook:agents:0.1.0').recipe == null
    [
      'dev.thecook:agents',
      'dev.thecook:agents:0.1',
      'dev.thecook:agents:0.1.0-SNAPSHOT',
      'dev.thecook:agents:[0.1,)',
      'dev.thecook:agents:main',
      'dev.thecook:agents:3f5a2c1'
    ]
    .each { text ->
      def error = shouldFail(IllegalArgumentException) { Coordinates.parse(text) }
      assert error.message.startsWith("Invalid recipe coordinates: ${text}")
    }
    assert Coordinates.written('acme:notes:1.2.3')
    assert !Coordinates.written('https://example.com/notes.groovy')
    assert !Coordinates.written('notes.groovy')
  }

  private static Throwable shouldFail(Class<? extends Throwable> type, Closure action) {
    try {
      action()
    } catch (Throwable error) {
      assert type.isInstance(error)
      return error
    }
    throw new AssertionError("Expected ${type.simpleName}")
  }
}
