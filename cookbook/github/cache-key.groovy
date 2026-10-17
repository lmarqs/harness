@help('''
Changes the key input of one cache step in a workflow job so the next run uses the supplied cache key expression.

## Before cooking

The workflow, the job and the step with the given `id` must exist in the composed content. The step must
accept a `key` input, and the supplied expression must describe the cache key the workflow needs.

## Cook

```sh
./cook dev.thecook:github:0.1.0:cache-key --workflow=.github/workflows/ci.yml --job=build \\
       --key='gradle-v2-${{ hashFiles("build.gradle", "settings.gradle") }}'
```

## Writes

| Target | Part | When |
| --- | --- | --- |
| The selected workflow file | `jobs.<job>.steps[id=<step>].with.key` | Always |

The next workflow run uses the supplied key expression for the selected cache step. The recipe does not
own the step's other inputs. Cooking the same arguments again leaves the key unchanged.

## With other recipes

Cook a recipe that supplies the cache step first, so this recipe can select its existing id. Distinct step
inputs compose independently; the last recipe declaring this key supplies it. `dev.thecook:github:0.1.0:aws-oidc`
declares the separate AWS credentials step and permission grants.
''')
import dev.thecook.github.Workflow

@param(value = ['workflow'], doc = 'Workflow path relative to the instance directory, such as .github/workflows/ci.yml') String workflow
@param(value = ['job'], doc = 'The job holding the cache step') String jobName
@param(value = ['step'], default = 'cache', doc = 'The cache step id') String step
@param(value = ['key'], doc = 'The new cache key expression') String key

@ingredient(Workflow) def ci

ci.file = workflow
ci.job(jobName).stepWith(step, 'key', key)
