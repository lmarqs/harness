# Agentic workflow fixtures

Each scenario's `initial/` tree is copied into a temporary destination repository. `__dot__` in a fixture path represents a leading dot, so hidden files remain visible in source listings. `${REPO}` in file content is replaced with the temporary repository path when a scenario needs an absolute path. `initial.links` records symbolic links as `path -> relative-target`; the copy step creates links without reading their targets.

The `expected/*.paths` files list the complete file and symbolic-link tree after each named step, including `.cook/state.json`. The workflow test checks contents, managed regions and keys, application identities, and preservation of handwritten data separately. The brownfield state starts at schema version one and owns the `git/ignore` block; its migration and preservation are part of the scenario.
