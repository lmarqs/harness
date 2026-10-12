# Migrating AWS credentials across old repositories

This is a **proposed interaction**, not a working CLI or supported schema. It tests whether a cookbook can carry an improved way of working back to repositories created before that improvement. The [AWS CI ingredient](../ingredients/aws-ci-credentials.yaml) describes job credentials; the [local AWS session ingredient](../ingredients/aws-session.yaml) describes a reusable mise check. The [migration recipe](../recipes/migrate-aws-credentials.yaml) handles adoption of existing jobs and tasks. A station could include these choices when starting a new repository; it does not replace the migration of an old one.

## The old setup is not one identical template

| Repository | Existing AWS path | What a migration must preserve |
| --- | --- | --- |
| `medprev-rest-api` | The build workflow passes access-key secrets to the shared credential action; local `node:setup` sources a CodeArtifact login helper. | npm installation and the local CodeArtifact path. |
| `medprev-web-app` | A composite CI action configures AWS, caches the pnpm store and `.venv`, then runs mise setup tasks; local `node:setup` authenticates to CodeArtifact. | Cache keys, task order, pnpm, and Python setup. |
| `medprev-management` | A composite CI action configures AWS and CodeArtifact, caches `~/.npm`, then runs `node:setup`. A deploy job uses a smaller mise setup. | The separate deploy path and npm cache. |
| `medprev-analytics-etl` | Several workflows configure AWS. Local `aws-auth` supplies CodeArtifact and ECR functions used by different tasks. | Per-job AWS use, pip token handling, ECR login, and separate Python environments. |

These observations come from the sibling repositories' workflows and `.mise/tasks` as inspected in October 2026. `libra` has no `.github` workflow to migrate; `betaflight-firmware-presets` does not use AWS in the inspected mise tasks. Neither should receive AWS CI files merely because the cookbook offers this ingredient.

## Example change: CI access keys to role assumption

Suppose the team has created the required IAM trust and roles, and decides its GitHub jobs should obtain AWS credentials through OIDC. Today the shared `../medprev-github-actions/configure-aws-credentials/action.yml` takes an access key and secret key, then assumes a role. Updating that action alone cannot remove the old secrets from every caller. A repository migration has to change the workflow's permissions, credential step, reusable-workflow inputs, and callers together.

Illustrative *before* in a build job:

```yaml
on:
  workflow_call:
    secrets:
      AWS_ACCESS_KEY_ID: { required: true }
      AWS_SECRET_ACCESS_KEY: { required: true }
jobs:
  main:
    steps:
      - uses: Medprev/medprev-github-actions/configure-aws-credentials@main
        with:
          awsAccessKeyId: ${{ secrets.AWS_ACCESS_KEY_ID }}
          awsSecretAccessKey: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
      - uses: Medprev/medprev-github-actions/codeartifact-auth@main
        with:
          packageType: npm
```

Illustrative *after*, for a job whose IAM role is already provisioned:

```yaml
jobs:
  main:
    permissions:
      contents: read
      id-token: write
    steps:
      - id: awsCredentials
        uses: aws-actions/configure-aws-credentials@v6
        with:
          aws-region: us-east-1
          role-to-assume: ${{ vars.BUILD_AWS_ROLE_ARN }}
      - uses: Medprev/medprev-github-actions/codeartifact-auth@main
        with:
          packageType: npm
```

The removed `workflow_call.secrets` entries and their callers need a separate dependency check: another job may still need one of those inputs. Adding job permissions can change other `GITHUB_TOKEN` access, so the full permission diff needs review. The role reference is per job, not a single value to stamp across build, test, and deploy. Cook must report an absent role, an unmatched old step, or an AWS consumer that would run before credentials are available. It must not invent an IAM role or write credential values.

## What using Cook should feel like

The intended flow for each old repository is: inspect its AWS jobs, choose the new cookbook ingredient and each job's role, preview a diff, apply, and run its affected CI jobs. The migration recipe coordinates this one-time transition. A possible interaction in `medprev-rest-api` is:

```text
$ ./cook migrate-aws-credentials
Found: build.yaml / main uses access-key secrets and CodeArtifact.
Found: node:setup has a local AWS session check and CodeArtifact login.
Choose a provisioned role for build.yaml / main: BUILD_AWS_ROLE_ARN
Preview: replace credential step; add id-token permission; update workflow callers;
         keep CodeArtifact after credentials; move the known local check to aws:check.
Unresolved: none. Review diff before applying.
```

That transcript is a target experience, not implemented output. After adoption, `kitchen copy` takes newer versions of the ingredients and `kitchen apply` updates only their owned contributions. A new repo can select the ingredients directly without running the migration recipe.

The existing `kitchen import` and `kitchen copy` proposals are not enough by themselves. `import` must associate an old step with a specific contribution, or the migration must match a known old form. A generic YAML writer needs to identify list entries by a stable key, remove old entries, and place the new credential step before its consumers. When the old form differs from the known pattern, Cook should stop and show the unresolved edit. Blindly replacing every AWS reference would break these repositories.

Local development is a related but separate path. `medprev-rest-api` and `medprev-web-app` check the current AWS session and configure npm; `medprev-analytics-etl` also builds pip index URLs and logs in to ECR. The `aws-session` ingredient contributes a mise `aws:check` task for the common validation. The migration recipe must replace each known inline check with a call to that task and leave its CodeArtifact or ECR functions intact. If it cannot isolate the old check from the rest of a helper, it stops for a manual edit. This only standardizes validation; the developer's preferred AWS login method is still a separate decision. The CI ingredient does not claim to configure local credentials.

Package caches are another migration target, but their keys depend on different inputs: `pnpm-lock.yaml`, `package-lock.json`, or the Python version source and requirements. A cookbook update should change the relevant cache contribution and its key together; it should preserve repo-specific caches such as management's Playwright browser cache.

**Success criterion:** one cookbook change yields a reviewable, repo-specific diff in each adopter, removes the superseded contribution, and leaves unrelated setup intact. If adopting an old workflow still requires rewriting every file by hand, this example has not delivered the promised value.
