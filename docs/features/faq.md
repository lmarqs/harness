# FAQ

## Objective

**Problem.** Readers need direct answers about what Cook changes, what teammates must install, how native settings
coexist, and how to remove or reverse changes without reading every reference page.

**Goal.** Give a short answer to each common question and link to the detailed contract.

## Overview

These answers describe the [intended architecture](../architecture.md). For today's build and usage instructions, read
[installation](installation.md), the [root README](../../README.md) and the cookbook's usage guides.

## General

### Does `the-cook` call a model or host coding agents?

The engine evaluates trusted Groovy recipes into an ordered batch of file operations. It does not host agents, launch
workers or supervise sessions. Recipes can configure agents through native files. Arbitrary recipe code can still
cause side effects outside the batch; approval is not a sandbox. See [engine boundaries](lifecycle.md).

### Is `the-cook` a coding-agent tool?

Agent configuration is one use. Ingredients model a program's native files and configuration, such as Git's ignore
file or Codex's settings and skills. Cook reviews and writes the resulting file operations. See
[concepts](concepts.md).

### Does it use MCP servers?

The engine's batch contract does not include MCP calls or external command execution. Recipes may declare a program's
native MCP configuration, and that program owns its connections and credentials. Permission and server configuration
are separate choices. See [program permissions](policy.md).

### Do my teammates need to install it?

Programs read committed native configuration directly. Teammates who invoke Cook need the engine. The wrapper
design obtains a pinned platform-specific jpackage package containing Java on first use and caches it, so package users
need no separate Java installation. That is the target delivery flow; the current repository launcher uses a local
development build. See [installation](installation.md) and [wrapper](wrapper.md).

### What is `cook`?

The intended `./cook` wrapper is a committed executable that pins an engine version and matching package checksums,
verifies its first-use download and caches the complete package. The repository's current
[`cook` script](../../cook) is a development launcher. See
[the current launcher](wrapper.md#current-repository-launcher).

### Do I have to invoke guideline skills by hand in every session?

That depends on the agent's discovery and loading rules. Recipes can declare skill files, instructions and native
discovery settings; the agent decides how and when to load and follow them. See [skills](skills.md) and
[repository guidance](context.md).

### Do I need a global install?

No. The target wrapper obtains its pinned package. Today's local packaging entry path is in
[installation](installation.md).

### Does CI need it?

CI consumes committed native files directly. A recipe can change CI or build configuration, so review that behaviour
in the diff. A job that invokes Cook follows ordinary whole-batch approval: `--batch-approve` displays the diff and
proceeds without prompting; noninteractive execution without it does not authorize writes. See
[compatibility](compatibility.md#cicd).

### Does it work offline?

Later invocations reuse the cached complete engine package. Recipes, templates and any dependencies they consume must
also be available; remote sources or arbitrary recipe code may need network access. History snapshots are recovery
records. See [air-gapped machines](wrapper.md#air-gapped-machines) and
[source resolution](render.md#destination-and-source-paths).

### Which coding agents and other workers are supported?

Consult the cookbook for the programs, native formats and versions its ingredients model. Cook configures a program
through its native files; the program enforces its own permissions. See
[supported program versions](compatibility.md#supported-program-versions).

### Where do ingredients, stations and recipes come from?

Cookbook repositories hold and ship ingredients and trusted Groovy recipes; recipes may also be local repository files.
Cookbook builds can be separate from engine builds. Ingredients are program data models, while station roles,
recipe/ingredient APIs, nesting and hooks require a separate discussion. See
[composition boundaries and cookbooks](stations.md).

### Can a repository have several recipes?

Yes. Supply paths inside the repository or `https://` URLs. Multiple recipes compose left to right; when two recipes
write the same section, key or file, the last one wins and becomes its owner. One combined diff shows the final result
and is the only notice. Recipe arguments follow their recipe as `--name=value`. See [CLI reference](cli.md).

### Is monitoring a recipe?

A recipe can configure monitoring through declared files. Running a monitor, responding to alerts and launching agents
are external activities. See [workflow configuration](lifecycle.md).

### Does Cook manage recipe versions for me?

No. Users supply the recipe version to apply. The committed `cook.lock` records each applied recipe's short name,
source and sha256, and each batch record keeps the evaluated and consumed sources, origins and arguments. Engine
wrapper pinning is separate. See [recipes](recipes.md) and [wrapper upgrades](wrapper.md#upgrading).

### Can I use my own version control system or worker?

Yes. A cookbook can provide models for its native configuration. See [ingredients](ingredients.md) and
[compatibility](compatibility.md).

### Does `the-cook` work with my loop runner or tracker?

Recipes can configure their native files. Task selection, assignments, synchronisation, sessions and completion signals
belong to those programs. See [loop runners](compatibility.md#loop-runners) and
[task trackers](compatibility.md#task-trackers).

### Does it depend on a task runner or a tool-version manager?

No. The CLI performs cooking; Gradle builds and packages Cook and cookbooks. A recipe's own prerequisites belong in
its cookbook guidance. See [task runners](compatibility.md#task-runners).

### Does it support monorepos?

The current working directory is the destination. Run from the root to target root files or from a subdirectory to
target that directory. Declared outputs cannot escape it, including through symlinks. An agent's lookup of root or
nested configuration remains program-specific. See [monorepos](compatibility.md#monorepos).

### Does it run on Windows?

The distribution design uses packages built for each target platform, with Java bundled. Current jpackage build
configuration includes a Windows launcher path; that does not establish a published Windows package or verified
Windows support. See
[local packaging](installation.md#build-a-local-package-today).

## Files and operations

### Does Cook require three configuration files?

No. The engine evaluates supplied recipes, and cookbook-specific inputs belong in cookbook guidance. See
[repository configuration choices](profile.md).

### Why are there both `.cook/` and `.agents/`?

Ignored destination-local `.cook/` holds batch records, source snapshots and reverse patches. `.agents/` is a useful
shared-content convention for agent instructions and skills where programs support it. See
[local history](../architecture.md#one-record-for-the-batch) and [skills](skills.md).

### Can I edit a program's native settings by hand?

Yes. A later apply proposes its declared changes against the current content, including your edits, and shows the
actual diff. Key and section targets preserve unrelated content; whole-file targets affect the whole file. Ownership
never blocks a change. See [apply](render.md).

### I edited inside a marked block. Will my text disappear?

A recipe targeting that section may propose replacing your edit. Markers name the owning recipe and region; they do
not protect the section from changes. Review the diff before approving; use narrower declarations or change the
recipe if the proposed result is wrong. See [marked blocks](render.md#managed-blocks).

### Why commit native configuration?

Shared native files let teammates and reviewers see the setup, and programs consume them directly. Whether a file
belongs in version control or remains personal is a repository choice. `cook.lock` is committed; batch records in
`.cook/` remain local and ignored. See [repository setup](scaffold.md).

### Does applying another recipe remove earlier outputs?

No. Applying B never removes output that A owns; if B writes a target A wrote, B wins and becomes its owner, and the
diff shows it. Applying a new version of A removes what A's previous version owned and the new version no longer
declares. Every apply invocation evaluates supplied recipes against current content; history never skips a recipe as
already applied, though the result can be an empty diff. See [ordered operations](render.md#ordered-operations).

### How do I stop using it?

You can stop invoking Cook and keep the native files. To undo a recipe's output, revert the batches that applied it;
discard is deferred. See [discard and revert](remove.md).

### Does discard restore what was there before?

Discard is deferred; when available, it removes what `cook.lock` lists for a recipe without restoring earlier values.
Revert restores the content from before a batch by applying its reverse patches. If an apply batch changed `test` from
`old` to `new`, reverting that batch proposes `new` to `old` and keeps later edits to other keys. If you have since
changed `test` itself, the revert writes nothing and names the conflicting file and key. See
[the comparison](remove.md#discard-and-revert-compared).

### Can I cook after losing `.cook/`?

Yes. Apply requires neither batch history nor `cook.lock`, and ownership survives in the committed `cook.lock`.
Losing `.cook/` loses the ability to revert earlier batches; reverting a batch requires its record. New batches still
save their records before target writes. See [history availability](../architecture.md#history-availability).

## Permissions and program behaviour

### Can a repository change a force-push deny?

Yes. Programs enforce their native rules. Review changes to permission settings and any separate controls such as
branch protection. See [program permissions](policy.md).

### Why is my organization's deny list not built in?

Organization choices belong in its cookbook recipes and reviewed native configuration. See
[ADR 0013](../adrs/0013-organization-content.md).

### How do we share our organization's choices across repositories?

Reuse trusted recipes with a separate invocation, diff and approval in each destination, adapting native configuration
and instructions to repository needs. External tools select repositories and deliver changes. See
[configuration across repositories](fleet.md).

### An allowed command still prompts. Why?

The program may apply overlapping rules, another settings scope, a trust requirement or a mode-specific decision.
Inspect its native settings and documented precedence. See
[permission review](policy.md#reviewing-permission-choices).

### Why does one program not enforce one of my denies?

Rule languages and enforcement support differ. Review each program's native output and keep support gaps explicit.
See [permission review](policy.md#reviewing-permission-choices).

### How do I change setup without losing important permission rules?

Read existing settings, choose declared targets deliberately and review the combined diff for both permission meaning
and unrelated content. Existing or edited files use ordinary whole-batch approval. See
[repository setup](scaffold.md).

### How do I let coding agents use a read-only MCP tool without a prompt?

Use each program's supported native permission settings, check what the rule matches and confirm enforcement in the
intended mode. A rule that works in one agent is not guaranteed to work in another. See [program permissions](policy.md).

### Can it configure notifications when an agent needs me?

A recipe can declare native hook settings and scripts where a program supports notifications. The program runs them;
Cook reviews and writes configuration. Review credentials, message content, dependencies and failure behaviour. See
[native program hooks](hooks.md).

### Does `the-cook` push or open pull requests?

Those are not batch actions. People, external tools and CI own delivery; Cook can configure their native files. Trusted
recipe code is executable and can cause arbitrary side effects, so this boundary is not a sandbox guarantee. See
[safety guidelines](safety.md).

### Does Cook verify an agent's work or enforce an unattended launch?

No. Native agent settings, task runners and CI have their own enforcement and check behaviour. Cook's batch history is
recovery information.

## Versions and updates

### Should we pin the engine version per repository?

The wrapper design commits an engine version and matching platform-package checksums. Cached packages serve that pin;
ordinary invocations do not select the newest release. The repository's current `cook` script is a development
launcher; see [wrapper](wrapper.md).

### How do I upgrade?

Explicitly change the committed engine version and matching package checksums together and review the change.
Cookbook sources and recipe versions are separate: users supply the recipe version they intend to apply. See
[upgrading](wrapper.md#upgrading).

### Is it safe to run a package that a committed script downloads?

Review the executable wrapper, engine version and checksums. The design verifies a package against the committed
checksum before execution. A checksum detects a mismatch; it does not by itself authenticate a publisher. Recipe trust
is a separate decision. See [wrapper security](wrapper.md#security).
