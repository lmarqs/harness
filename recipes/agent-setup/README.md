# Agent setup aliases

`codex.groovy` and the sibling `../agent-setup.groovy` delegate to [cookbook](../cookbook/README.md). Both configure Codex and Claude and default the policy to `.agents/policy.yaml`.

```sh
./cook agent-setup --dry-run
./cook agent-setup --policy=path/to/policy.yaml --skills-home=/path/to/shared-skills
./cook recipes/agent-setup/codex.groovy --set policy=path/to/policy.yaml --dry-run
```

The aliases forward preferences and set `scriptDirectory` to the delegated recipe directory, so relative policy paths resolve from the repository root. See cookbook for supported arguments, generated files, adoption, and validation.
