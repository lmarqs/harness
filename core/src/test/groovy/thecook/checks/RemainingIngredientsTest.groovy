package thecook.checks

import org.junit.Test
import groovy.json.JsonSlurper
import org.tomlj.Toml
import org.yaml.snakeyaml.Yaml
import thecook.composition.Kitchen
import static thecook.checks.ModelChecks.*

class RemainingIngredientsTest {
    @Test void migratedNativeIngredientContracts() {
        int first = ModelChecks.results.size()
        run()
        def failures = ModelChecks.results.drop(first).findAll { !it.passed }
        assert failures.empty: failures
    }

    static void run() {
        check('Runtime pins and package setup compose through mise and Git consumes only selected paths') { root ->
            def book = CookbookChecks.cookbook()
            def kitchen = new Kitchen(root)
            def mise = book.ingredient('mise', kitchen)
            def python = book.ingredient('python', kitchen, [version: '3.12', mise: mise])
            def go = book.ingredient('go', kitchen, [version: '1.23', mise: mise])
            def terraform = book.ingredient('terraform', kitchen, [version: '1.9', mise: mise])
            def pip = book.ingredient('uv-pip', kitchen, [python: python, mise: mise])
            def aws = book.ingredient('aws-session', kitchen, [mise: mise])
            def git = book.ingredient('git', kitchen)
            git.ignore.from(python.files.local)
            git.ignore.from(python.files.ephemeral)
            [mise, python, go, terraform, pip, aws, git].each { it.mount() }
            kitchen.plan().apply()
            def config = Toml.parse(new File(root, 'mise.toml').text)
            assert config.getString('tools.python') == '3.12'
            assert config.getString('tools.go') == '1.23'
            assert config.getString('tools.terraform') == '1.9'
            assert config.getTable('env._.python.venv').getBoolean('create')
            assert new File(root, '.mise/tasks/python/setup').text.contains('uv pip install')
            assert new File(root, '.mise/tasks/python/setup').text.contains('python3 -m pip')
            assert new File(root, '.mise/tasks/aws/check').text.contains('aws sts get-caller-identity')
            assert new File(root, '.gitignore').text.contains('/.venv/')
            assert !new File(root, 'go.mod').exists()
            assert !new File(root, 'requirements.txt').exists()
            assert kitchen.plan().changes.empty
        }
        check('OS and editor factories expose local paths without writing files') { root ->
            def book = CookbookChecks.cookbook(); def kitchen = new Kitchen(root)
            ['linux', 'macos', 'windows', 'visualstudiocode'].each { name ->
                def component = book.ingredient(name, kitchen)
                assert !component.files.local.value.empty
                component.mount()
            }
            assert kitchen.plan().changes.empty
        }
        check('Compose service contributions preserve existing services in the selected native config') { root ->
            write(root, 'docker-compose.yml', 'services:\n  existing:\n    image: busybox\n')
            def book = CookbookChecks.cookbook(); def kitchen = new Kitchen(root)
            book.ingredient('docker', kitchen).mount()
            def compose = book.ingredient('docker-compose', kitchen,
                [path: 'docker-compose.yml', services: [api: [image: 'application:latest']], config: [name: 'application']])
            compose.services.from([[cache: [image: 'redis:7']]])
            compose.mount(); kitchen.plan().apply()
            def config = new Yaml().load(new File(root, 'docker-compose.yml').text)
            assert config.services.existing.image == 'busybox'
            assert config.services.api.image == 'application:latest'
            assert config.services.cache.image == 'redis:7'
            assert new File(root, '.dockerignore').text.contains('.git/')
            assert !new File(root, '.gitignore').exists()
        }
        check('AWS credentials use explicit placement and preserve other jobs and permission entries') { root ->
            write(root, '.github/workflows/ci.yaml', 'jobs:\n  lint:\n    steps: []\n  deploy:\n    permissions:\n      packages: write\n    steps:\n      - id: checkout\n        uses: actions/checkout@v4\n      - id: deploy\n        run: ./deploy.sh\n')
            def book = CookbookChecks.cookbook(); def kitchen = new Kitchen(root)
            book.ingredient('aws-ci-credentials', kitchen, [workflow: '.github/workflows/ci.yaml',
                job: 'deploy', region: 'us-east-1', role: 'arn:aws:iam::123456789012:role/deploy',
                placement: [before: 'deploy'], adopt: true,
                steps: [[id: 'checkout', uses: 'actions/checkout@v4'], [id: 'deploy', run: './deploy.sh']]]).mount()
            kitchen.plan().apply()
            def config = new Yaml().load(new File(root, '.github/workflows/ci.yaml').text)
            assert config.jobs.deploy.steps*.id == ['checkout', 'awsCredentials', 'deploy']
            assert config.jobs.deploy.permissions.packages == 'write'
            assert config.jobs.deploy.permissions['id-token'] == 'write'
            assert config.jobs.lint.steps.empty
        }
        check('Host targets render selected native instructions and config contributions') { root ->
            def book = CookbookChecks.cookbook(); def kitchen = new Kitchen(root)
            book.target('claude', kitchen, [instructions: 'Run the project checks.', settings: [permissions: [allow: ['Bash(mise run check)']]]]).mount()
            book.target('codex', kitchen, [instructions: 'Use mise tasks.', config: [model: 'selected-model'],
                hooks: [hooks: [:]], rules: ['project.rules': 'prefix_rule(pattern=["mise", "run", "check"], decision="allow")\n']]).mount()
            kitchen.plan().apply()
            assert new File(root, 'CLAUDE.md').text.contains('Run the project checks.')
            assert new File(root, 'AGENTS.md').text.contains('Use mise tasks.')
            assert new JsonSlurper().parse(new File(root, '.claude/settings.json')).permissions.allow == ['Bash(mise run check)']
            assert Toml.parse(new File(root, '.codex/config.toml').text).getString('model') == 'selected-model'
            assert new File(root, '.codex/rules/project.rules').text.contains('prefix_rule')
            assert !new File(root, '.claude/settings.local.json').exists()
        }
    }
}
