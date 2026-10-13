# Init recipe

`agentic/init` bootstraps the selected harnesses with permissions and shared context. Select one or both with repeatable `--harness=codex` and `--harness=claude` options. Context seeds `AGENTS.md` when absent and connects Claude through a managed import block.

For a selected harness without a permission application, init uses `.agents/policy.yaml`. If that file is absent, it seeds a version 1 policy with no instructions or command grants. An existing starter policy must be valid before init writes anything. Existing selected permission applications keep their policy selections and native configuration. A later `agentic/permission` run can update an initialized harness using the same application identity.

```sh
./cook agentic/init --harness=claude --harness=codex
```

Remove one harness's generated configuration with `./cook agentic/init --harness=claude --remove` or the corresponding Codex choice. The starter policy and `AGENTS.md` remain repository owned.

Run its supplier compilation, fixture test, formatting, and static checks independently:

```sh
./gradlew -p cookbook/recipes/agentic/init check
```

This command builds init and its declared recipe dependencies. Full distribution verification uses the repository root `./gradlew check cookbookBuild`.
