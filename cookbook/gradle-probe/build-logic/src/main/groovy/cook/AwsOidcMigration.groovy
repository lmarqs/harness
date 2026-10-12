package cook

import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.yaml.snakeyaml.nodes.SequenceNode
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = 'Disposable migration probe')
abstract class AwsOidcMigration extends StagedMigration {
    @Input abstract Property<String> getRole()
    private static final String LEGACY = 'Medprev/medprev-github-actions/configure-aws-credentials@main'
    private static final String OIDC = 'aws-actions/configure-aws-credentials@v6'
    private static final List SECRETS = ['AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY']

    void migrate(File directory) {
        def build = new File(directory, '.github/workflows/build.yaml')
        def caller = new File(directory, '.github/workflows/main.yaml')
        if (!build.isFile() || !caller.isFile()) throw new GradleException('This probe requires build.yaml and its main.yaml caller')
        def workflow = new YamlEdits(build.text)
        def main = new YamlEdits(caller.text)
        def steps = workflow.at(['jobs', 'main', 'steps'])
        if (!(steps instanceof SequenceNode)) throw new GradleException('Unknown build job layout')
        def matches = (0..<steps.value.size()).findAll {
            workflow.scalar(['jobs', 'main', 'steps', it, 'uses']) in [LEGACY, OIDC]
        }
        if (matches.size() != 1) throw new GradleException('Expected exactly one known AWS credential step')
        def step = ['jobs', 'main', 'steps', matches[0]]
        if (workflow.scalar(step + ['uses']) == LEGACY) {
            if (workflow.at(step + ['id']) || workflow.keys(step + ['with']).sort() != ['awsAccessKeyId', 'awsSecretAccessKey'].sort()) {
                throw new GradleException('Legacy AWS step has extra inputs or outputs; requires a reviewed migration')
            }
            workflow.setScalar(step + ['uses'], OIDC)
            workflow.replaceMapping(step + ['with'], "role-to-assume: ${role.get()}\naws-region: us-east-1\nrole-session-name: github.com")
        } else if (workflow.scalar(step + ['with', 'role-to-assume']) != role.get()) {
            throw new GradleException('Existing OIDC role differs from requested role')
        }
        grantOidc(workflow, ['jobs', 'main'])
        if (main.scalar(['jobs', 'build', 'uses']) != './.github/workflows/build.yaml') throw new GradleException('Unknown reusable workflow caller')
        grantOidc(main, ['jobs', 'build'])
        SECRETS.each { secret ->
            if (workflow.text.contains('secrets.' + secret) || workflow.text.contains('secrets[')) {
                throw new GradleException("Another build step may consume ${secret}; requires review")
            }
            workflow.remove(['on', 'workflow_call', 'secrets', secret])
            main.remove(['jobs', 'build', 'secrets', secret])
        }
        if (workflow.keys(['on', 'workflow_call', 'secrets']).isEmpty()) workflow.remove(['on', 'workflow_call', 'secrets'])
        if (main.keys(['jobs', 'build', 'secrets']).isEmpty()) main.remove(['jobs', 'build', 'secrets'])
        build.text = workflow.text
        caller.text = main.text
    }

    private static void grantOidc(YamlEdits doc, List job) {
        // Refuse to guess defaults: adding permissions can revoke existing implicit access.
        if (!doc.at(job + ['permissions'])) throw new GradleException("Declare explicit existing permissions at ${job} before migrating to OIDC")
        if (doc.at(job + ['permissions', 'id-token'])) doc.setScalar(job + ['permissions', 'id-token'], 'write')
        else doc.prepend(job + ['permissions'], 'id-token: write')
    }
}
