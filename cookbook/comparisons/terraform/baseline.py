#!/usr/bin/env python3
"""Run the local_file ownership comparison offline in a temporary directory."""

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--terraform', default='terraform')
    parser.add_argument('--provider-mirror', required=True,
                        help='Unpacked Terraform provider mirror containing hashicorp/local 2.9.1')
    parser.add_argument('--results', type=Path,
                        default=Path(__file__).resolve().parent / 'results.json')
    args = parser.parse_args()
    fixture = Path(__file__).resolve().parent
    with tempfile.TemporaryDirectory(prefix='the-cook-terraform-baseline-') as directory:
        work = Path(directory)
        shutil.copy(fixture / 'main.tf', work / 'main.tf')
        shutil.copytree(fixture / 'modules', work / 'modules')
        config = work / 'offline.tfrc'
        mirror = json.dumps(str(Path(args.provider_mirror).resolve()))
        config.write_text('disable_checkpoint = true\nprovider_installation {\n'
                          ' filesystem_mirror {\n path = ' + mirror + '\n'
                          ' include = ["registry.terraform.io/hashicorp/local"]\n }\n}\n')
        env = dict(os.environ, TF_CLI_CONFIG_FILE=str(config),
                   CHECKPOINT_DISABLE='1', TF_IN_AUTOMATION='1')

        def tf(*command, accepted=(0,)):
            process = subprocess.run([args.terraform, *command], cwd=work, env=env,
                                     text=True, capture_output=True)
            if process.returncode not in accepted:
                raise RuntimeError(' '.join(command) + '\n' + process.stdout + '\n' + process.stderr)
            return process

        def plan(name, enabled=True):
            target = work / (name + '.tfplan')
            command = ['plan', '-input=false', '-no-color', '-detailed-exitcode', '-out=' + str(target)]
            if not enabled:
                command.append('-var=git_enabled=false')
            process = tf(*command, accepted=(0, 2))
            view = json.loads(tf('show', '-json', str(target)).stdout)
            changes = [change['change']['actions'] for change in view.get('resource_changes', [])]
            return target, changes, process.returncode

        def apply(enabled=True):
            command = ['apply', '-input=false', '-no-color', '-auto-approve']
            if not enabled:
                command.append('-var=git_enabled=false')
            tf(*command)

        tf('init', '-input=false', '-no-color')
        results = []
        output = work / 'output/.gitignore'
        output.parent.mkdir(exist_ok=True)
        output.write_text('# Team\nmanual/\n')
        apply()
        expected = '# cook:begin git/ignore\nnode_modules/\nnpm-debug.log*\n# cook:end git/ignore\n'
        assert output.read_text() == expected
        results.append({'case': 'existing_manual_file_at_first_apply',
                        'observed': 'replaced_by_complete_resource_content', 'manual_prefix_preserved': False})

        _, actions, code = plan('idempotent')
        assert code == 0
        results.append({'case': 'unchanged_second_plan', 'exit_code': code, 'actions': actions})

        output.write_text(expected + '# Manual addition\nmanual/\n')
        _, actions, _ = plan('manual-drift')
        assert actions == [['create']]
        apply()
        assert output.read_text() == expected
        results.append({'case': 'manual_addition_then_normal_plan_apply', 'actions': actions,
                        'observed': 'resource_recreated_and_manual_addition_overwritten'})

        apply(False)
        assert not output.exists()
        results.append({'case': 'module_disabled_with_unchanged_file', 'observed': 'whole_file_deleted'})

        apply()
        output.write_text(expected + '# Manual addition\nmanual/\n')
        _, actions, code = plan('refreshed-unmount', False)
        assert actions == [] and code == 0
        apply(False)
        assert output.read_text() == expected + '# Manual addition\nmanual/\n'
        results.append({'case': 'module_disabled_after_manual_addition_with_refresh', 'actions': actions,
                        'observed': 'modified_file_left_unmanaged_with_generated_block_still_present'})

        apply()
        saved, actions, _ = plan('saved-unmount', False)
        assert actions == [['delete']]
        output.write_text(expected + '# Manual addition after preview\nmanual/\n')
        tf('apply', '-input=false', '-no-color', '-auto-approve', str(saved))
        assert not output.exists()
        results.append({'case': 'manual_edit_after_saved_module_removal_plan', 'actions': actions,
                        'observed': 'whole_file_deleted_including_edit_after_preview'})

        apply()
        output.write_text(expected + '# Manual change before preview\n')
        saved, actions, _ = plan('saved-recreation')
        assert actions == [['create']]
        output.write_text(expected + '# Different manual change after preview\n')
        tf('apply', '-input=false', '-no-color', '-auto-approve', str(saved))
        assert output.read_text() == expected
        results.append({'case': 'manual_edit_after_saved_recreation_plan', 'actions': actions,
                        'observed': 'complete_content_overwritten_without_file_drift_rejection'})

        imported = tf('import', '-input=false', '-no-color', '-state=import-only.tfstate',
                      'module.git[0].local_file.ignore', str(output), accepted=(1,))
        assert 'not support import' in imported.stderr.lower() or 'import not implemented' in imported.stderr.lower()
        results.append({'case': 'native_local_file_import', 'observed': 'provider_rejects_import',
                        'diagnostic': imported.stderr.strip()})

        version = json.loads(tf('version', '-json').stdout)['terraform_version']
        report = {'terraform_version': version, 'local_provider_version': '2.9.1',
                  'execution': 'offline filesystem provider mirror; all fixtures under /tmp; no cloud resources',
                  'cases': results,
                  'sources': ['https://registry.terraform.io/providers/hashicorp/local/latest/docs/resources/file',
                              'https://github.com/hashicorp/terraform-provider-local/blob/main/internal/provider/resource_local_file.go']}
        args.results.parent.mkdir(parents=True, exist_ok=True)
        args.results.write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
