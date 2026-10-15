# refresh-ci-cache

Changes the key of one cache step in a workflow job, so the next run starts from a fresh cache.

```sh
./cook dev.thecook:refresh-ci-cache --workflow=.github/workflows/ci.yml --job=build \
       --key='gradle-v2-${{ hashFiles("**/*.gradle") }}'
```

| Target | Part |
| --- | --- |
| the workflow file | `jobs.<job>.steps[id=<step>].with.key` |
