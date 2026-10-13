package thecook.cli

import java.nio.file.Files
import java.nio.file.StandardCopyOption

class Fixtures {
  static void copy(String name, File destination) {
    def resource = Fixtures.getResource('/fixtures/' + name)
    assert resource != null: "Missing fixture: ${name}"
    def source = new File(resource.toURI()).toPath()
    Files.walk(source).withCloseable { paths ->
      paths.forEach { path ->
        def relative = source.relativize(path).toString()
        // A suffix lets Gradle copy names such as .gitignore that it normally excludes.
        if (relative.endsWith('.fixture')) relative = relative.substring(0, relative.length() - '.fixture'.length())
        def target = destination.toPath().resolve(relative)
        if (Files.isDirectory(path)) {
          Files.createDirectories(target)
          return
        }
        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
      }
    }
  }
}
