# migrate-aws-credentials

`migrate-aws-credentials` replaces the steps of selected CI workflow jobs with a caller-supplied list that includes an
AWS credentials step assuming an IAM role, and grants each job the `contents: read` and `id-token: write` permissions.

## Usage

```groovy
def checkout = [id: 'checkout', uses: 'actions/checkout@v4']
def staticKeys = [id: 'staticKeys', run: './export-aws-keys.sh']
def build = [id: 'build', run: './gradlew build']
book.recipe('migrate-aws-credentials', kitchen, [
    adopt: true,
    jobs: [[workflow: '.github/workflows/build.yaml', job: 'build',
        expectedSteps: [checkout, staticKeys, build], steps: [checkout, build],
        region: 'us-east-1', role: 'arn:aws:iam::123456789012:role/build',
        placement: [before: 'build']]]
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `adopt` | yes | none | Boolean; must be `true`. |
| `jobs` | yes | none | Nonempty list of job maps, described below. |
| `files` | no | none | List of maps with `path`, `expected` and `content`, where `expected` and `content` are strings: guarded whole-file replacements. A `path` cannot be a workflow named in `jobs`. |
| `id` | no | `migrate-aws-credentials` | Identity of the recipe component. |

Each `jobs` entry is a map with these keys:

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `workflow` | yes | none | Path of the existing workflow YAML file. |
| `job` | yes | none | Identifier of the job under `jobs`. |
| `expectedSteps` | yes | none | List that must equal the job's current `steps`, as parsed from the YAML. |
| `steps` | yes | none | List of step maps: the job's complete new steps, without the credentials step. No step may have the id `awsCredentials`. |
| `region` | yes | none | AWS region, passed to the credentials step as `aws-region`. |
| `role` | yes | none | IAM role ARN, passed to the credentials step as `role-to-assume`. |
| `placement` | yes | none | Position of the credentials step: `'start'`, `'end'`, `[before: '<step id>']` or `[after: '<step id>']`. |
| `action` | no | `aws-actions/configure-aws-credentials@v6.3.0` | Action reference of the credentials step. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `<workflow>` | YAML | For each job: `jobs.<job>.steps`, `jobs.<job>.permissions.contents` set to `read`, and `jobs.<job>.permissions.id-token` set to `write`. |
| each `files` path | Text | The whole file, set to `content`. |

The new `steps` value is the job's `steps` argument with the credentials step inserted at `placement`. The credentials
step is `{id: awsCredentials, uses: <action>, with: {aws-region: <region>, role-to-assume: <role>}}`.

## Inputs

- The recipe reads each job's workflow file and each `files` path; all of them must exist.
- Runs of the updated workflow need the IAM role in `role` to accept the job's identity token. The recipe does not
  create or check the role.

## Behaviour

- The recipe fails when `adopt` is not `true`, when `jobs` is empty or not a list, when a job's `expectedSteps` differ
  from its current steps or the job is missing, when a step is not a map, when `placement` has another form, when a
  `before` or `after` selector matches no step id or more than one, or when a `files` path is also a job's workflow.
- A `files` entry whose current content equals neither `expected` nor `content` fails the recipe.
- The recipe takes over the job keys and the `files` paths, replacing existing values. Other jobs, other keys of the
  job and other permission entries stay unchanged.
- After an application the job's steps include the credentials step, so applying again needs `expectedSteps` that
  match the updated job.
- Each job is applied through the `aws-ci-credentials` ingredient, with identity `aws-ci-credentials-<index>` for the
  job's position in `jobs`.
