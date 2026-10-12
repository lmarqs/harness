# Ten station candidates

These are **concept cards, not supported schema or ready presets**. A station proposes initial repository choices and team-owned files. The [composition stress test](../examples/composability-stress-test.md) records where combining them currently fails.

1. [git-repository](git-repository.yaml)
2. [node-npm](node-npm.yaml)
3. [python-uv](python-uv.yaml)
4. [go-service](go-service.yaml)
5. [docker-image](docker-image.yaml)
6. [compose-dev](compose-dev.yaml)
7. [local-aws-stack](local-aws-stack.yaml)
8. [beads-tracker](beads-tracker.yaml)
9. [ralph-beads](ralph-beads.yaml)
10. [ralph-prd](ralph-prd.yaml)

Examples 6 and 7 compete for Compose ownership; 9 and 10 compete for a single default tracker. A real `station apply` must report these conflicts rather than keep whichever value arrived first.
