package thecook.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.GroovyCompile
import thecook.groovy.SupplierCompiler

// Packaging convention only. Each ingredient owns its build, release version and tests.
class IngredientPlugin implements Plugin<Project> {
  protected String getDefinitionsDirectory() {
    'ingredients'
  }
  void apply(Project project) {
    ['groovy', 'java-library', 'maven-publish'].each { project.pluginManager.apply(it) }
    project.group = project.providers.gradleProperty('group').get()
    project.version = project.providers.gradleProperty('version').get()
    project.repositories.mavenCentral()
    project.dependencies.add('implementation', project.dependencies.localGroovy())
    project.dependencies.add('api', 'dev.thecook:the-cook-core:0.1.0')
    def sets = project.extensions.getByType(SourceSetContainer)
    sets.main.groovy.setSrcDirs([project.projectDir])
    sets.main.groovy.include { details -> Character.isUpperCase(details.file.name.charAt(0)) && details.file.name.endsWith('.groovy') }
    sets.main.resources.setSrcDirs([])
    def compilerScript = project.layout.buildDirectory.file('cook/supplier-compiler.groovy')
    def compilerConfig = project.tasks.register('cookCompilerConfig') {
      inputs.property('configuration', SupplierCompiler.script(project.name))
      outputs.file(compilerScript)
      doLast {
        def target = compilerScript.get().asFile
        target.parentFile.mkdirs()
        target.setText(SupplierCompiler.script(project.name), 'UTF-8')
      }
    }
    project.tasks.withType(GroovyCompile).configureEach {
      dependsOn compilerConfig
      groovyOptions.configurationScript = compilerScript.get().asFile
    }
    def definitionPath = project.providers.gradleProperty('cookbookPath').getOrElse(project.name)
    def definitionDirectory = definitionsDirectory
    project.tasks.named('processResources') {
      from(project.projectDir) {
        include '*.groovy', '*.sh', '*.hbs', 'templates/**'
        exclude 'build.gradle', 'settings.gradle'
        into("META-INF/the-cook/${definitionDirectory}/${definitionPath}")
      }
    }
    project.tasks.named('jar') {
      manifest.attributes('Implementation-Title': project.name,
      'Implementation-Version': project.version.toString(), 'Cook-Model-Api': '1')
    }
    project.extensions.getByType(PublishingExtension).publications.create('ingredient', MavenPublication) {
      from(project.components.java)
    }
  }
}
