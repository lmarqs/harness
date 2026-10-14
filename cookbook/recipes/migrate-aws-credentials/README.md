# migrate-aws-credentials

Inserts an OIDC credentials step into one workflow job and grants it the permissions that step needs. A job that
already has the step is rejected.

```sh
./cook dev.thecook:migrate-aws-credentials --workflow=.github/workflows/deploy.yml --job=deploy \
       --region=us-east-1 --role=arn:aws:iam::123456789012:role/deploy --placement=after:checkout
```

| Target | Part |
| --- | --- |
| the workflow file | `jobs.<job>.steps`, `jobs.<job>.permissions.contents`, `jobs.<job>.permissions.id-token` |
