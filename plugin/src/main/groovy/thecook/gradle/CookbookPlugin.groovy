package thecook.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.tomlj.Toml

/**
 * Packages a cookbook from its root, where `cookbook.toml` names its `group` and release `version`. Each top-level
 * directory is one artifact: a jar of the directory's files under its package path, `<group>/<dir>/`, with a POM.
 * The `cookbook` BOM lists every artifact at the release. `publish` writes them to the Maven-layout repository at
 * `build/repository`. Packaging compiles nothing, because the engine compiles recipe sources when it applies them.
 */
class CookbookPlugin implements Plugin<Project> {
  static final String BOM = 'cookbook'

  void apply(Project project) {
    ['base', 'maven-publish'].each { project.pluginManager.apply(it) }
    def manifest = Toml.parse(project.file('cookbook.toml').getText('UTF-8'))
    project.group = manifest.getString('group')
    project.version = manifest.getString('version')
    def publishing = project.extensions.getByType(PublishingExtension)
    publishing.repositories.maven {
      name = 'cookbook'
      url = project.layout.buildDirectory.dir('repository')
    }
    def packagePath = project.group.toString().replace('.', '/')
    def artifacts = project.projectDir.listFiles()
        .findAll { it.isDirectory() && !it.name.startsWith('.') && it.name != 'build' }
        *.name.sort()
    if (BOM in artifacts) {
      throw new IllegalStateException("A cookbook directory cannot be named ${BOM}: the BOM uses that artifact name")
    }
    artifacts.each { artifact ->
      def jar = project.tasks.register("${artifact}Jar", Jar) {
        group = 'cookbook'; description = "Package the ${artifact} artifact"
        archiveBaseName.set(artifact)
        destinationDirectory.set(project.layout.buildDirectory.dir('libs'))
        preserveFileTimestamps = false
        reproducibleFileOrder = true
        from(project.file(artifact)) { into("${packagePath}/${artifact}") }
      }
      project.tasks.named('assemble') { dependsOn(jar) }
      publishing.publications.create(artifact, MavenPublication) {
        artifactId = artifact
        it.artifact(jar)
      }
    }
    publishing.publications.create(BOM, MavenPublication) {
      artifactId = BOM
      pom.packaging = 'pom'
      pom.withXml { xml ->
        def dependencies = xml.asNode().appendNode('dependencyManagement').appendNode('dependencies')
        artifacts.each { artifact ->
          def dependency = dependencies.appendNode('dependency')
          dependency.appendNode('groupId', project.group.toString())
          dependency.appendNode('artifactId', artifact)
          dependency.appendNode('version', project.version.toString())
        }
      }
    }
  }
}
