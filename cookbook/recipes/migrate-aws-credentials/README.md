# migrate-aws-credentials

Declares an OIDC credentials step in one workflow job and grants it the permissions that step needs. An absent step is
inserted at the placement; applying again changes nothing.

```sh
./cook dev.thecook:migrate-aws-credentials --workflow=.github/workflows/deploy.yml --job=deploy \
       --region=us-east-1 --role=arn:aws:iam::123456789012:role/deploy --placement=after:checkout
```

| Target | Part |
| --- | --- |
| the workflow file | `jobs.<job>.steps[id=awsCredentials]`, `jobs.<job>.permissions.contents`, `jobs.<job>.permissions.id-token` |
