package cook

import groovy.json.JsonSlurper
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = 'Lock generation invokes a package manager')
abstract class PackageManagerMigration extends StagedMigration {
    @Input abstract Property<String> getManager()
    @Input abstract Property<String> getYarnExecutable()
    @Input abstract Property<String> getNpmExecutable()
    @Input abstract Property<Boolean> getOffline()
    @Inject abstract ExecOperations getExecOperations()
    static final String YARN_CONFIG = 'nodeLinker: node-modules\nenableGlobalCache: false\n'

    void migrate(File directory) {
        if (!(manager.get() in ['npm', 'yarn'])) throw new GradleException('manager must be npm or yarn')
        def manifest = new File(directory, 'package.json')
        def data = new JsonSlurper().parse(manifest)
        if (data.workspaces || data.overrides || data.pnpm || data.resolutions) throw new GradleException('Workspaces and package-manager-specific resolution rules require a separate migration')
        if (data.scripts.values().any { it =~ /\b(npm|npx|yarn|pnpm)\b/ }) throw new GradleException('Package scripts embed a package manager; requires review')
        def target = manager.get()
        def version = target == 'yarn' ? '4.17.0' : '10.9.8'
        def workflowDirectory = new File(directory, '.github/workflows')
        workflowDirectory.eachFile { file ->
            if (file.name != 'node.yaml' && file.isFile() && (file.text =~ /\b(npm ci|npm install|npm run|npx |yarn install|yarn run|codeartifact-auth)\b/).find()) {
                throw new GradleException("Unmigrated package-manager integration in ${file.name}; requires review")
            }
        }
        if (!(data.packageManager ==~ /(npm|yarn)@\d+\.\d+\.\d+/)) throw new GradleException('This probe requires an explicit npm or Yarn packageManager version')
        // Preserve every byte outside the one known scalar.
        manifest.text = manifest.text.replaceFirst(/"packageManager"\s*:\s*"[^"]*"/, '"packageManager": "' + target + '@' + version + '"')
        def manifestText = manifest.text
        def ignore = new File(directory, '.gitignore')
        def oldBlock = ~/(?s)# cook:package-manager\n.*?# end:package-manager\n/
        if (!(ignore.text =~ oldBlock).find()) throw new GradleException('Missing explicit package-manager ignore block')
        ignore.text = ignore.text.replaceFirst(oldBlock, '# cook:package-manager\n/' + (target == 'yarn' ? 'package-lock.json' : 'yarn.lock') + '\n# end:package-manager\n')
        def mise = new File(directory, 'mise.toml')
        mise.text = mise.text.replaceAll(/(?m)^yarn = "[^"]*"\n/, '')
        if (target == 'yarn') mise.text = mise.text.replace('[tools]\n', '[tools]\nyarn = "' + version + '"\n')
        def config = new File(directory, '.yarnrc.yml')
        if (config.exists() && config.text != YARN_CONFIG) throw new GradleException('Custom Yarn configuration requires review')
        def oldLock = new File(directory, target == 'yarn' ? 'package-lock.json' : 'yarn.lock')
        def workflow = new File(directory, '.github/workflows/node.yaml')
        def yaml = new YamlEdits(workflow.text)
        def steps = yaml.at(['jobs', 'test', 'steps'])
        def install = (0..<steps.value.size()).findAll { yaml.scalar(['jobs', 'test', 'steps', it, 'id']) == 'install' }
        def cache = (0..<steps.value.size()).findAll { yaml.scalar(['jobs', 'test', 'steps', it, 'id']) == 'cache' }
        if (install.size() != 1 || cache.size() != 1) throw new GradleException('Unknown Node workflow layout')
        def installPath = ['jobs', 'test', 'steps', install[0], 'run']
        if (!(yaml.scalar(installPath) in ['npm ci', 'yarn install --immutable'])) throw new GradleException('Unknown install command')
        yaml.setScalar(installPath, target == 'yarn' ? 'yarn install --immutable' : 'npm ci')
        def cachePath = ['jobs', 'test', 'steps', cache[0], 'with']
        yaml.setScalar(cachePath + ['path'], target == 'yarn' ? '.yarn/cache' : '~/.npm')
        def lock = target == 'yarn' ? 'yarn.lock' : 'package-lock.json'
        yaml.setScalar(cachePath + ['key'], '${{ runner.os }}-' + target + '-${{ hashFiles(\'' + lock + '\') }}')
        workflow.text = yaml.text
        def executable = target == 'yarn' ? yarnExecutable.get() : npmExecutable.get()
        def reportedVersion = new ByteArrayOutputStream()
        execOperations.exec { commandLine executable, '--version'; standardOutput = reportedVersion }
        if (reportedVersion.toString('UTF-8').trim() != version) throw new GradleException("Expected ${target} ${version}; executable reports ${reportedVersion.toString('UTF-8').trim()}")
        if (target == 'yarn') {
            config.text = YARN_CONFIG
            execOperations.exec {
                workingDir directory
                commandLine yarnExecutable.get(), 'install', '--mode=skip-build'
                environment 'YARN_ENABLE_IMMUTABLE_INSTALLS', 'false'
                environment 'YARN_ENABLE_SCRIPTS', 'false'
                if (offline.get()) environment 'YARN_ENABLE_NETWORK', 'false'
            }
        } else {
            if (config.exists()) config.delete()
            execOperations.exec {
                workingDir directory
                commandLine npmExecutable.get(), 'install', '--package-lock-only', '--ignore-scripts', '--no-audit', '--no-fund'
                environment 'npm_config_cache', new File(temporaryDir, 'npm-cache').absolutePath
                if (offline.get()) environment 'npm_config_offline', 'true'
            }
        }
        if (oldLock.exists()) oldLock.delete()
        if (new JsonSlurper().parse(manifest) != new JsonSlurper().parseText(manifestText)) throw new GradleException('Package manager changed manifest semantics; requires review')
        manifest.text = manifestText // Package managers may reformat JSON while generating a lockfile.
        fileSystem.delete { delete(new File(directory, 'node_modules'), new File(directory, '.yarn')) }
    }
}
