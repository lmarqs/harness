# FAQ

## Objective

**Problem.** Readers need direct answers about what Cook changes, what teammates must install, how native settings
coexist, and how to remove or reverse changes without reading every reference page.

**Goal.** Give a short answer to each common question and link to the detailed contract.

## Overview

These answers describe the [intended architecture](../architecture.md). Target requirements do not establish that the
behaviour is implemented. For today's build and usage instructions, read [installation](installation.md), the
[root README](../../README.md) and the cookbook's usage guides.

## General

### Does `the-cook` call a model or host coding agents?

The engine evaluates trusted Groovy recipes into an ordered batch of file operations. It does not host agents, launch
workers or supervise sessions. Recipes can configure agents through native files. Arbitrary recipe code can still
cause side effects outside the batch; approval is not a sandbox. See [engine boundaries](lifecycle.md).

### Is `the-cook` a coding-agent tool?

Agent configuration is one use. Ingredients model a program's native files and configuration, such as Git's ignore
file or Codex's settings and skills. Cook reviews and writes the resulting file operations; it does not own a fixed
software development lifecycle. See [concepts](concepts.md).

### Does it use MCP servers?

The engine's batch contract does not include MCP calls or external command execution. Recipes may declare a program's
native MCP configuration, and that program owns its connections and credentials. Permission and server configuration
are separate choices. See [program permissions](policy.md).

### Do my teammates need to install it?

Programs can read committed native configuration without Cook. Teammates who invoke Cook need the engine. The wrapper
design obtains a pinned platform-specific jpackage package containing Java on first use and caches it, so package users
need no separate Java installation. That is the target delivery flow; there is no published release, and the current
repository launcher uses a local development build. See [installation](installation.md) and [wrapper](wrapper.md).

### What is `cook`?

The intended `./cook` wrapper is a committed executable that pins an engine version and matching package checksums,
verifies its first-use download and caches the complete package. Its pin format and cache layout are not selected here.
The repository's current [`cook` script](../../cook) is a development launcher. See
[the current launcher](wrapper.md#current-repository-launcher).

### Do I have to invoke guideline skills by hand in every session?

That depends on the agent's discovery and loading rules. Recipes can declare skill files, instructions and native
discovery settings; Cook does not inject them or guarantee an agent follows them. See [skills](skills.md) and
[repository guidance](context.md).

### Do I need a global install?

The target wrapper obtains its pinned package. A global install is not required by that design, and no global-binary
deferral protocol is specified. Today's local packaging entry path is in [installation](installation.md).

### Does CI need it?

CI does not need Cook merely to consume native files. A recipe can change CI or build configuration, so review that
behaviour in the diff. A job that invokes Cook follows ordinary whole-batch approval: `--batch-approve` displays the
diff and proceeds without prompting; noninteractive execution without it does not authorize writes. There is no
mandatory Cook CI check. See [compatibility](compatibility.md#cicd).

### Does it work offline?

A cached complete engine package needs no repeat download. Recipes, templates and any dependencies they consume must
also be available; remote sources or arbitrary recipe code may need network access. History snapshots are recovery
records, not an implicit offline source resolver for later invocations. See
[air-gapped machines](wrapper.md#air-gapped-machines) and [source resolution](render.md#destination-and-source-paths).

### Which coding agents and other workers are supported?

Consult the cookbook for the programs, native formats and versions its ingredients model. Cook can configure a program
without launching it or guaranteeing its permission enforcement. See
[supported program versions](compatibility.md#supported-program-versions).

### Where do ingredients, stations and recipes come from?

Cookbook repositories hold and ship ingredients and trusted Groovy recipes; recipes may also be local repository files.
Cookbook builds can be separate from engine builds. Ingredients remain program data models, while station roles,
recipe/ingredient APIs, nesting and hooks require a separate discussion. No archive schema, registry or station command
is established here. See [composition boundaries and cookbooks](stations.md).

### Can a repository have several recipes?

Yes. Supply exact file paths or supported source URIs. Multiple recipes compose left to right, and later operations can
replace earlier values at the same target. One combined diff shows the final result. Recipe arguments follow their
recipe as `--name=value`; there is no implicit cookbook-name lookup. See [CLI reference](cli.md).

### Is monitoring a recipe?

A recipe can configure monitoring through declared files. Running a monitor, responding to alerts and launching agents
are external activities, not batch actions. See [workflow configuration](lifecycle.md).

### Does Cook manage recipe versions for me?

No. Users supply the recipe version to apply or discard. Cook records evaluated and consumed recipe/template sources,
origins and arguments for recovery; that does not establish persistent recipe identity or a recipe-update protocol.
Engine wrapper pinning is separate. See [recipes](recipes.md) and [wrapper upgrades](wrapper.md#upgrading).

### Can I use my own version control system or worker?

A cookbook can provide models for its native configuration. Cook does not require version-control, worker or jail
contracts to apply recipes, nor does it manage branches or launch workers. See [ingredients](ingredients.md) and
[compatibility](compatibility.md).

### Does `the-cook` work with my loop runner or tracker?

Recipes can configure their native files. Task selection, assignments, synchronisation, sessions and completion signals
belong to those programs. There is no Cook attempt or lifecycle protocol for them to invoke. See
[loop runners](compatibility.md#loop-runners) and [task trackers](compatibility.md#task-trackers).

### Does it depend on a task runner or a tool-version manager?

Neither is required by the intended cooking interface. The CLI performs cooking; Gradle builds and packages Cook and
cookbooks. A recipe's own prerequisites belong in its cookbook guidance. See [task runners](compatibility.md#task-runners).

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

No. The engine evaluates supplied recipes, and cookbook-specific inputs belong in cookbook guidance. It does not
require a repository profile, policy and hooks schema. See [repository configuration choices](profile.md).

### Why are there both `.cook/` and `.agents/`?

Ignored destination-local `.cook/` holds operation history, source snapshots and recovery patches. `.agents/` is a useful
shared-content convention for agent instructions and skills where programs support it, not an engine-required layout.
See [local history](../architecture.md#one-record-for-the-batch) and [skills](skills.md).

### Can I edit a program's native settings by hand?

Yes. A later apply proposes its declared changes against the current content, including your edits, and shows the
actual diff. Key and section targets preserve unrelated content; whole-file targets affect the whole file. There is no
ownership veto, `--adopt`, adoption alias or separate adoption step. See [apply](render.md).

### I edited inside a marked block. Will my text disappear?

A recipe targeting that section may propose replacing your edit. Markers define boundaries, not protection from
changes. Review the diff before approving; use narrower declarations or change the recipe if the proposed result is
wrong. See [marked blocks](render.md#managed-blocks).

### Why commit native configuration?

Shared native files let teammates and reviewers see the setup, and programs consume them without needing Cook to read
them. Whether a file belongs in version control or remains personal is a repository choice. Recovery history in
`.cook/` remains local and ignored. See [repository setup](scaffold.md).

### Does applying another recipe remove earlier outputs?

Only its declared targets are affected. Applying B does not implicitly remove unrelated output from A. Every
apply/discard invocation evaluates supplied recipes against current content; history never skips a recipe as already
applied, though the result can be an empty diff. See [ordered operations](render.md#ordered-operations).

### How do I stop using it?

You can stop invoking Cook and keep the native files. To delete contributions, discard the supplied recipe version;
to restore a recorded run's prior values, revert that run using its history. There is no eject or automatic
repository-wide restoration. See [discard and revert](remove.md).

### Does discard restore what was there before?

Discard deletes declared contributions even after manual edits. Revert restores recorded prior values. If an apply
changed `test` from `old` to `new` and you then changed it to `custom`, discarding that recipe proposes deleting `test`;
reverting the original run proposes `custom` to `old`. Both preserve unrelated keys and show the actual diff. See
[the comparison](remove.md#discard-and-revert-compared).

### Can I cook after losing `.cook/`?

Apply and discard require neither prior history nor an ownership database. Losing `.cook/` loses local recovery
information; reverting a particular run requires that run's history. New operations still save recovery patches before
target writes. See [history availability](../architecture.md#history-availability).

## Permissions and program behaviour

### Can a repository change a force-push deny?

Programs enforce their native rules. Cook does not impose an immutable ingredient safety floor. Review changes to
permission settings and any separate controls such as branch protection. See [program permissions](policy.md).

### Why is my organization's deny list not built in?

Organization choices belong in its cookbook recipes and reviewed native configuration, rather than mandatory engine
policy. See [ADR 0013](../adrs/0013-organization-content.md).

### How do we share our organization's choices across repositories?

Reuse trusted recipes with a separate invocation, diff and approval in each destination, adapting native configuration
and instructions to repository needs. External tools select repositories and deliver changes. This does not select a
station API or an automatic rollout service. See [configuration across repositories](fleet.md).

### An allowed command still prompts. Why?

The program may apply overlapping rules, another settings scope, a trust requirement or a mode-specific decision.
Inspect its native settings and documented precedence; Cook defines no universal decision order. See
[permission review](policy.md#reviewing-permission-choices).

### Why does one program not enforce one of my denies?

Rule languages and enforcement support differ. Review each program's native output and keep support gaps explicit.
Cook does not promise universal translation. See [permission review](policy.md#reviewing-permission-choices).

### How do I change setup without losing important permission rules?

Read existing settings, choose declared targets deliberately and review the combined diff for both permission meaning
and unrelated content. Existing or edited files use ordinary whole-batch approval without an import-equivalence gate.
See [repository setup](scaffold.md).

### How do I let coding agents use a read-only MCP tool without a prompt?

Use each program's supported native permission settings, check what the rule matches and confirm enforcement in the
intended mode. A rule that works in one agent is not guaranteed to work in another. See [program permissions](policy.md).

### Can it configure notifications when an agent needs me?

A recipe can declare native hook settings and scripts where a program supports notifications. The program runs them;
Cook reviews and writes configuration. Review credentials, message content, dependencies and failure behaviour. Cook
provides no session-event dispatcher. See [native program hooks](hooks.md).

### Does `the-cook` push or open pull requests?

Those are not batch actions. People, external tools and CI own delivery; Cook can configure their native files. Trusted
recipe code is executable and can cause arbitrary side effects, so this boundary is not a sandbox guarantee. See
[safety guidelines](safety.md).

### Does Cook verify an agent's work or enforce an unattended launch?

No. Native agent settings, task runners and CI have their own enforcement and check behaviour. Cook's run history is
recovery information, not verification proof. Agent-session orchestration remains future work in
[issue #6](https://github.com/lmarqs/the-cook/issues/6); personal setup backup remains in
[issue #5](https://github.com/lmarqs/the-cook/issues/5).

## Versions and updates

### Should we pin the engine version per repository?

The wrapper design commits an engine version and matching platform-package checksums. Cached packages serve that pin;
ordinary invocations do not select the newest release. This target does not establish that the current development
launcher implements it. See [wrapper](wrapper.md).

### How do I upgrade?

Explicitly change the committed engine version and matching package checksums together and review the change. That does
not upgrade cookbook sources, manage recipe versions or automatically reapply recipes. See [upgrading](wrapper.md#upgrading).

### Is it safe to run a package that a committed script downloads?

Review the executable wrapper, engine version and checksums. The design verifies a package against the committed
checksum before execution. A checksum detects a mismatch; it does not by itself authenticate a publisher or establish
a signing-key protocol. Recipe trust is a separate decision. See [wrapper security](wrapper.md#security).
