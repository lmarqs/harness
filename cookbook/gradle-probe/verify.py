#!/usr/bin/env python3
"""Exercise the real Gradle plugin against disposable repository copies."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parent
GRADLE = os.environ.get('COOK_PROBE_GRADLE', str(ROOT / 'gradlew'))
YARN = os.environ.get('COOK_PROBE_YARN', 'yarn')
NPM = os.environ.get('COOK_PROBE_NPM', 'npm')
REPORT = ROOT / 'build/reports'
checks = []


def digest(root):
    return {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in root.rglob('*') if p.is_file()
            and 'node_modules' not in p.parts and '.yarn' not in p.parts}


def gradle(repo, case, task, *args, error=None):
    command = [GRADLE, '-p', str(ROOT), task, f'-Pcase={case}', f'-Prepo={repo}',
               f'-PyarnExecutable={YARN}', f'-PnpmExecutable={NPM}', '-PofflineInstall=true',
               '--console=plain', *args]
    started = time.monotonic()
    result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    REPORT.mkdir(parents=True, exist_ok=True)
    (REPORT / 'last-run.log').write_text(result.stdout)
    if error:
        assert result.returncode != 0 and error in result.stdout, result.stdout
    else:
        assert result.returncode == 0, result.stdout
    return result.stdout, round(time.monotonic() - started, 3)


def passed(name):
    checks.append(name)
    print(f'PASS {name}', flush=True)


def copy_fixture(name, destination):
    shutil.copytree(ROOT / 'fixtures' / name, destination)
    (destination / 'unmanaged.txt').write_text('Personal local file; keep me.\n')
    return destination


def check_installed(repo, manager):
    environment = dict(os.environ, YARN_ENABLE_NETWORK='false', YARN_ENABLE_SCRIPTS='false')
    command = [YARN, 'install', '--immutable', '--mode=skip-build'] if manager == 'yarn' else [
        NPM, 'ci', '--ignore-scripts', '--offline', '--no-audit', '--no-fund',
        '--cache', str(ROOT / 'build/npm-cache')]
    subprocess.run(command, cwd=repo, env=environment, check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    subprocess.run(['node', 'test.js'], cwd=repo, check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)


with tempfile.TemporaryDirectory(prefix='cook-gradle-verify-') as scratch:
    base = Path(scratch)
    node = copy_fixture('node', base / 'node')
    initial = digest(node)
    gradle(node, 'node', 'plan')
    plan = json.loads((REPORT / 'plan.json').read_text())
    shutil.copyfile(REPORT / 'changes.patch', REPORT / 'node-switch.patch')
    assert digest(node) == initial
    assert {c['path'] for c in plan['changes']} == {
        'package.json', 'package-lock.json', 'yarn.lock', '.yarnrc.yml',
        '.gitignore', 'mise.toml', '.github/workflows/node.yaml'}
    passed('Plan stages a real Yarn lockfile without changing the source repository')

    # A reviewed plan must not silently incorporate newer edits.
    package = node / 'package.json'
    original = package.read_text()
    package.write_text(original.replace('"keepThis": true', '"keepThis": "changed after preview"'))
    drifted = digest(node)
    gradle(node, 'node', 'applyPlan', error='Changed since preview: package.json')
    assert digest(node) == drifted
    passed('Drift rejects the entire apply before any file is written')
    package.write_text(original)

    gradle(node, 'node', 'applyPlan')
    assert package.read_text() == original.replace('npm@10.9.8', 'yarn@4.17.0')
    assert json.loads(package.read_text())['packageManager'] == 'yarn@4.17.0'
    assert json.loads(package.read_text())['localSetting'] == {'keepThis': True}
    assert (node / 'unmanaged.txt').read_text() == 'Personal local file; keep me.\n'
    assert '.env.local\n' in (node / '.gitignore').read_text()
    assert 'APP_MODE = "development"' in (node / 'mise.toml').read_text()
    assert 'run: echo "team customization"' in (node / '.github/workflows/node.yaml').read_text()
    check_installed(node, 'yarn')
    passed('Applied Yarn project installs offline and runs its dependency test; custom content survives')

    gradle(node, 'node', 'plan')
    assert json.loads((REPORT / 'plan.json').read_text())['changes'] == []
    output, warm_seconds = gradle(node, 'node', 'plan')
    assert ':switchPackageManager UP-TO-DATE' in output
    passed('Repeat planning is idempotent; Gradle skips an unchanged migration')

    ignore = node / '.gitignore'
    ignore.write_text(ignore.read_text() + '# New local exclusion\nlocal-output/\n')
    gradle(node, 'node', 'plan')
    assert (ROOT / 'build/stages/node/.gitignore').read_text() == ignore.read_text()
    assert json.loads((REPORT / 'plan.json').read_text())['changes'] == []
    passed('Gradle tracks subsequent .gitignore edits and carries them into the staged result')

    gradle(node, 'node', 'plan', '-Pmanager=npm')
    gradle(node, 'node', 'applyPlan')
    assert json.loads(package.read_text())['packageManager'] == 'npm@10.9.8'
    assert not (node / 'yarn.lock').exists() and not (node / '.yarnrc.yml').exists()
    check_installed(node, 'npm')
    passed('Yarn to npm takes plan and apply; regenerated npm lockfile installs and tests offline')

    aws = copy_fixture('aws', base / 'aws')
    before = digest(aws)
    caller_before = (aws / '.github/workflows/main.yaml').read_text().split('  test:\n')[1]
    gradle(aws, 'aws', 'plan')
    shutil.copyfile(REPORT / 'changes.patch', REPORT / 'aws-oidc.patch')
    assert digest(aws) == before
    gradle(aws, 'aws', 'applyPlan')
    workflow = (aws / '.github/workflows/build.yaml').read_text()
    caller = (aws / '.github/workflows/main.yaml').read_text()
    assert 'aws-actions/configure-aws-credentials@v6' in workflow
    assert 'id-token: write' in workflow and 'id-token: write' in caller
    assert 'contents: write' in workflow and 'checks: write' in workflow
    assert 'AWS_ACCESS_KEY_ID' not in workflow
    assert caller.split('  test:\n')[1] == caller_before
    assert digest(aws)['.github/workflows/test.yaml'] == before['.github/workflows/test.yaml']
    assert 'codeartifact-auth@main' in workflow
    passed('OIDC updates the workflow and selected caller while retaining other secret consumers')
    gradle(aws, 'aws', 'plan')
    assert json.loads((REPORT / 'plan.json').read_text())['changes'] == []
    passed('OIDC migration is idempotent')

    legacy = copy_fixture('aws-legacy', base / 'legacy')
    before = digest(legacy)
    gradle(legacy, 'aws', 'plan', error='Declare explicit existing permissions')
    assert digest(legacy) == before
    passed('Unmodified production workflow is rejected because its permission baseline is implicit')

    unknown = copy_fixture('aws', base / 'unknown')
    workflow = unknown / '.github/workflows/build.yaml'
    workflow.write_text(workflow.read_text().replace('          awsAccessKeyId:', '          roleToAssume: custom-role\n          awsAccessKeyId:'))
    before = digest(unknown)
    gradle(unknown, 'aws', 'plan', error='extra inputs or outputs')
    assert digest(unknown) == before
    passed('Unknown legacy credential options require review without touching the source')

    combined = copy_fixture('node', base / 'combined')
    shutil.copytree(ROOT / 'fixtures/combined', combined, dirs_exist_ok=True)
    gradle(combined, 'combined', 'plan')
    paths = {c['path'] for c in json.loads((REPORT / 'plan.json').read_text())['changes']}
    assert 'yarn.lock' in paths and '.github/workflows/build.yaml' in paths
    gradle(combined, 'combined', 'applyPlan')
    check_installed(combined, 'yarn')
    passed('Independent Node and AWS workflows migrate together through Gradle task outputs')

    combined = copy_fixture('node', base / 'combined-unsupported')
    shutil.copytree(ROOT / 'fixtures/aws', combined, dirs_exist_ok=True)
    before = digest(combined)
    gradle(combined, 'combined', 'plan', error='Unmigrated package-manager integration')
    assert digest(combined) == before
    passed('Composition rejects the existing AWS build that would retain npm and npm-specific registry auth')

    report = {'passed': len(checks), 'checks': checks, 'warm_unchanged_plan_seconds': warm_seconds,
              'scope': 'Local fixtures; no AWS authentication, live CI, or private package registry execution'}
    (REPORT / 'verification.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
