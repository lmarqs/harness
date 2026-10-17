package thecook.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.nio.file.Files
import java.nio.file.Path

class MavenLocalPluginTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder()

  @Test void aCookbookOutsideThisRepositoryAppliesThePluginFromMavenLocal() {
    def repository = copy(Path.of(System.getProperty('thecook.mavenLocal')), folder.newFolder('m2').toPath())
    def version = System.getProperty('thecook.pluginVersion')
    assert new File(repository, "dev/thecook/cookbook/dev.thecook.cookbook.gradle.plugin/${version}").isDirectory()
    def book = folder.newFolder('book')
    new File(book, 'settings.gradle').text = '''pluginManagement {
  repositories {
    mavenLocal()
    mavenCentral()
  }
}
rootProject.name = 'book'
'''
    new File(book, 'build.gradle').text = "plugins {\n  id 'dev.thecook.cookbook' version '${version}'\n}\n"
    new File(book, 'cookbook.toml').text = 'group = "acme"\nversion = "1.2.3"\napi = 1\n'
    new File(book, 'notes').mkdirs()
    new File(book, 'notes/notes.groovy').text = "@help('Notes.')\nimport java.util.List\n"

    GradleRunner.create().withProjectDir(book)
        .withArguments('publishToMavenLocal', "-Dmaven.repo.local=${repository}")
        .build()

    assert new File(repository, 'acme/notes/1.2.3/notes-1.2.3.jar').isFile()
    assert new File(repository, 'acme/cookbook/1.2.3/cookbook-1.2.3.pom').isFile()
  }

  private static File copy(Path source, Path target) {
    Files.walk(source).withCloseable { paths ->
      paths.forEach { Path path ->
        Files.copy(path, target.resolve(source.relativize(path).toString()),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      }
    }
    target.toFile()
  }
}
