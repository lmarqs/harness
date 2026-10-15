@help('''
Declares an AWS OIDC credentials step in one workflow job and grants the permissions that step needs.
An absent step is inserted at the selected placement; an existing step with the same id is replaced.

## Before applying

The selected workflow job must exist. A `before:<id>` or `after:<id>` placement names an existing step.
The AWS role must trust the workflow's OIDC identity for the credentials step to assume it when the job runs.

## Apply

```sh
./cook dev.thecook:github:0.1.0:aws-oidc --workflow=.github/workflows/deploy.yml --job=deploy \\
       --region=us-east-1 --role=arn:aws:iam::123456789012:role/deploy --placement=after:checkout
```

## Writes

| Target | Part | When |
| --- | --- | --- |
| The selected workflow file | `jobs.<job>.steps[id=awsCredentials]`, owned whole | Always |
| The selected workflow file | `jobs.<job>.permissions.contents`, set to `read` | Always |
| The selected workflow file | `jobs.<job>.permissions.id-token`, set to `write` | Always |

The next workflow run uses the selected region and IAM role through its `awsCredentials` step. Applying
the same arguments again leaves the declared step and grants unchanged.

## With other recipes

`dev.thecook:github:0.1.0:cache-key` changes a separate step input in the same workflow. Distinct jobs,
step ids and permission grants compose independently; the last recipe declaring the same target supplies it.
''')
@confirm('''
The selected job's next run assumes the selected IAM role in AWS, with whatever access that role grants.
''')
import dev.thecook.aws.Aws
import dev.thecook.github.Workflow

@param(value = ['workflow'], doc = 'Workflow path relative to the instance directory, such as .github/workflows/ci.yml') String workflow
@param(value = ['job'], doc = 'The job to change') String jobName
@param(value = ['region'], doc = 'AWS region') String region
@param(value = ['role'], doc = 'IAM role to assume') String role
@param(value = ['placement'], default = 'start', doc = 'start, end, before:<step id> or after:<step id>') String placement

@ingredient(Workflow) def ci
@ingredient(Aws) def aws

ci.file = workflow
def job = ci.job(jobName)
job.step(aws.credentialsStep(region, role), placement)
job.permissions(aws.oidcPermissions())
