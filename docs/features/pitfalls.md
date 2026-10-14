# Pitfalls, gaps and traps

## Objective

**Problem.** Recipe evaluation, native permission settings, source paths and reversal can behave differently from a
reader's expectations. A correct-looking setting or successful file write does not establish that a program loaded
the setting, enforced the rule or tested the work.

**Goal.** Explain the relevant traps, the boundary of Cook's responsibility and what to review before relying on a
setup.

## Overview

This page follows the [intended architecture](../architecture.md). Each entry gives the symptom, its cause, Cook's
responsibility and the reader's next step. Program-specific details belong in the cookbook's guidance and the
consuming program's documentation. See [safety](safety.md) for batch guarantees and [the glossary](glossary.md) for
terms.

- [Permissions](#permissions)
- [Recipes and trust](#recipes-and-trust)
- [Program configuration](#program-configuration)
- [Checks](#checks)
- [Paths and environment](#paths-and-environment)
- [Ordered operations and recovery](#ordered-operations-and-recovery)
- [Versions and the wrapper](#versions-and-the-wrapper)

## Permissions

### A prefix matches the leading words only

- **Symptom.** A deny intended for `kubectl patch` misses a spelling with options before the subcommand.
- **Why.** A program's prefix matcher may compare only leading words. Options, absolute paths and interpreter wrappers
  can change those words.
- **Cook's responsibility.** Review and write declared native settings.
- **What you do.** Inspect the consuming program's rule language and the variants the rule must cover. Choose a broader
  deny where necessary rather than assuming a narrow prefix covers all forms. See [program permissions](policy.md).

### A task runner runs whatever its tasks say

- **Symptom.** An allowed test command performs an unexpected action after its script changes.
- **Why.** Permission to start a command can also permit the code it invokes. A script or task definition may itself be
  editable by the agent that runs it.
- **Cook's responsibility.** Configure declared files; the consuming program enforces command permissions.
- **What you do.** Review scripts as code and keep production credentials out of sessions that can edit and execute
  them. Treat permission to edit and run a script as permission to execute its changed content.

### One program cannot enforce every rule form

- **Symptom.** A deny works in one agent and has no effect in another.
- **Why.** Native rule languages and enforcement boundaries differ. One program may approve tool calls while another
  uses rules for escalation from its own sandbox.
- **Cook's responsibility.** Expose declared configuration for review.
- **What you do.** Review the native rule and its support in each program and mode. Keep unsupported choices explicit.
  See [permission review](policy.md#reviewing-permission-choices).

### An `ask` choice stalls an unattended session

- **Symptom.** An agent or external runner waits for a person who is absent.
- **Why.** Native approval behavior depends on program mode, defaults and other settings scopes.
- **Cook's responsibility.** Write the declared settings.
- **What you do.** Review approval choices for the program and mode the external runner actually uses. Keep human
  approval where intended and account for that stop in the runner's operation.

### Settings outside the project change permissions

- **Symptom.** The same project configuration prompts or allows differently on two machines.
- **Why.** Local, user or managed settings, trust and program mode may affect the result.
- **Cook's responsibility.** Change declared targets only.
- **What you do.** Compare the settings scopes and modes on both machines. Decide what belongs in shared files before
  declaring changes. See [repository setup](scaffold.md).

### MCP permission rules do not define servers

- **Symptom.** A permission rule names a tool unavailable in another clone.
- **Why.** Server definitions, availability and credentials are separate from permission rules.
- **Cook's responsibility.** Recipes may declare native configuration.
- **What you do.** Document the required servers and credential setup, then review tool approval separately. Keep
  credentials out of committed instructions and settings.

### A local command follows configuration to a remote system

- **Symptom.** A local migration or seed task changes a shared database.
- **Why.** The executable is local, but environment variables or files can name a remote service.
- **Cook's responsibility.** File configuration does not constrain where a later program command acts. Arbitrary recipe
  code can also read its environment and act outside the batch.
- **What you do.** Review service destinations and credential scope before allowing commands or evaluating trusted
  recipes. Do not infer isolation from the working directory.

## Recipes and trust

### Batch approval does not sandbox a recipe

- **Symptom.** A recipe causes an external effect before its diff is approved, or the effect remains after rollback.
- **Why.** Recipes are trusted executable Groovy code. Evaluation can do more than declare file operations.
- **Cook's responsibility.** Diff review, destination boundaries, reverse patches and rollback govern declared batch
  operations. Executing commands is not a batch action; arbitrary recipe effects are outside those guarantees.
- **What you do.** Read and trust sources before evaluation, including dependencies. Approving the resulting diff is a
  separate decision. See [recipes](recipes.md) and [safety](safety.md).

### Text read by an agent can become instructions

- **Symptom.** An agent follows unexpected directions from a task, comment, skill or generated instruction file.
- **Why.** Agents act on text they read; a configuration file or skill can influence behavior without enforcing it.
- **Cook's responsibility.** Review declared file changes.
- **What you do.** Review shared guidance and sources, treat untrusted text as data and use the agent's own controls.
  See [repository guidance and context](context.md).

### A source checksum is not a trust decision

- **Symptom.** Reviewed bytes match a checksum but contain unwanted executable behavior.
- **Why.** A checksum establishes matching content, not whether its author or effects are acceptable.
- **Cook's responsibility.** The engine wrapper verifies its package against the committed pin. This does not
  authenticate or sandbox cookbook recipes.
- **What you do.** Review source origins and recipe changes separately from engine package integrity. Users manage the
  recipe versions they supply. See [cookbooks](cookbooks.md#cookbooks).

### Executable code can read inherited secrets

- **Symptom.** A token intended for one integration is available to other code running in the same environment.
- **Why.** Recipe code and program hook scripts can read the variables available to their processes. Keeping a token
  out of committed files does not keep it out of executable code.
- **Cook's responsibility.** Batch diff review and recovery provide no confinement or secret-redaction guarantee for
  arbitrary recipe effects or a program's hooks.
- **What you do.** Limit credentials in each process environment and review all code that can access them. Check what
  hook scripts put in notifications and logs. See [native program hooks](hooks.md).

## Program configuration

### A program ignores settings in an untrusted folder

- **Symptom.** Project hooks or rules do not take effect in another working copy.
- **Why.** Loading settings and inheriting trust are program-specific; a nested folder does not guarantee either.
- **Cook's responsibility.** Write declared configuration.
- **What you do.** Inspect that program's lookup and trust rules in the actual folder. See [workspace
  conventions](workspaces.md).

### A native hook has missing dependencies or a different working directory

- **Symptom.** Formatting or notifications work on one machine and fail on another.
- **Why.** The consuming program runs hooks with its own environment, event support and working directory.
- **Cook's responsibility.** Declare and review hook settings and scripts.
- **What you do.** Check executable availability, path quoting, credentials and failure behavior. Make repeated
  formatting safe. Treat feedback hooks separately from CI evidence. See [native program hooks](hooks.md).

### Shared instructions cost attention and may be loaded twice

- **Symptom.** An agent misses task details or reads the same guidance through several discovery paths.
- **Why.** Instructions compete for attention, and overlapping imports or skill discovery may repeat them.
- **Cook's responsibility.** Write declared guidance.
- **What you do.** Keep frequently loaded guidance concise, use focused topic files and remove duplicate references.
  Consult the program's loading rules. See [skills](skills.md) and [context](context.md).

## Checks

### A check passes with zero tests, or with unrelated tests

- **Symptom.** A check succeeds while the change remains untested.
- **Why.** A runner may accept a selection that matches no tests; passing unrelated tests provides no relevant evidence.
- **Cook's responsibility.** Recipes can configure check commands and instructions; Cook does not verify test
  selection or results.
- **What you do.** Select tests that exercise the change, report what ran and configure the runner's no-match behavior.
  See [repository checks and configuration](verify.md).

## Paths and environment

### Tool globs pick up temporary files

- **Symptom.** A linter, test runner or editor scans batch records or nested working copies.
- **Why.** A tool's include patterns may not honor version-control ignore rules.
- **Cook's responsibility.** Keep batch records in the destination's ignored `.cook/`. Recipes may declare other
  native exclusions.
- **What you do.** Review each tool's exclusions for `.cook/` and any temporary working-copy directories. See
  [workspace conventions](workspaces.md).

### Working copies inherit parent configuration and share services

- **Symptom.** A working copy uses parent dependencies, or parallel checks collide on ports and databases.
- **Why.** Programs may search parent directories, while separate directories still share machine services.
- **Cook's responsibility.** Write the declared file setup.
- **What you do.** Prepare dependencies in the intended working copy and configure unique service names or external
  locks where needed. Working-copy creation and execution belong to external tools. See [workspaces](workspaces.md).

### Running from a subdirectory changes the destination

- **Symptom.** A recipe targets files under a subdirectory when root configuration was intended.
- **Why.** The current working directory is the destination.
- **Cook's responsibility.** Contain declared writes and deletions within that directory, including symlink resolution.
- **What you do.** Invoke from the intended destination and inspect target paths. Separately check how programs discover
  root or nested settings. See [monorepos](compatibility.md#monorepos).

### Moving a recipe changes its relative source references

- **Symptom.** A recipe cannot find a template, or consumes a different source after it is copied elsewhere.
- **Why.** Relative recipe/template references resolve from the referencing recipe's original location or source URL.
  A local recipe or reference outside the repository is refused.
- **Cook's responsibility.** Capture consumed sources and record their origins, which preserves relative resolution
  when the sources are snapshotted.
- **What you do.** Review origins and referenced sources when relocating recipes. The executable's location does not
  choose the destination. See [source paths](apply.md#destination-and-source-paths).

## Ordered operations and recovery

### A later recipe replaces an earlier value

- **Symptom.** A key ends with B's value when A and B both declare it.
- **Why.** Recipes and operations compose left to right; overlap is intentional.
- **Cook's responsibility.** Show one diff from current content to the final combined result.
- **What you do.** Review operand order and target scope. A whole-file declaration is broader than a key or section
  declaration. See [ordered operations](apply.md#ordered-operations).

### A manual edit is changed by the next apply

- **Symptom.** A recipe proposes replacing a hand-edited value or marked section.
- **Why.** Every invocation evaluates supplied recipes against current content; ownership never blocks a change.
- **Cook's responsibility.** Show the actual diff for whole-batch approval. Noninteractive execution without
  `--batch-approve` does not authorize writes.
- **What you do.** Review the proposed result and adjust declarations or recipe inputs if needed. Omitting an earlier
  recipe does not withdraw its unrelated contributions. See [apply](apply.md).

### Revert refuses after an overlapping edit

- **Symptom.** Reverting a batch that changed `test` from `old` to `new` writes nothing after a manual edit to `test`.
- **Why.** Revert uses the batch's reverse patches against current content without reevaluating recipes. A later
  edit that overlaps the batch's changes conflicts with them.
- **Cook's responsibility.** Write nothing and list each conflicting file and section.
- **What you do.** Resolve the listed conflicts, then revert again. See [revert](revert.md).

### A lost batch record prevents reversal of that batch

- **Symptom.** A batch cannot be reverted after its `.cook/batches/<batchId>/` record is lost.
- **Why.** Its reverse patches come from that [batch record](../architecture.md#one-record-for-the-batch).
- **Cook's responsibility.** Apply needs neither batch records nor `cook.lock`; ownership survives in the committed
  `cook.lock`.
- **What you do.** Retain the batch records you may need and inspect failure records. Do not expect file reversal to
  undo external program activity. See [history availability](../architecture.md#batch-record-availability).

## Versions and the wrapper

These entries concern the [wrapper design](wrapper.md). The current repository launcher uses a local build.

### The first run needs access to the pinned package

- **Symptom.** An uncached engine package cannot be obtained on a fresh machine.
- **Why.** The wrapper needs the pinned platform package, including Java, before it can run. Local recipes alone do not
  supply that package, and remote recipe sources may separately require network access.
- **Cook's responsibility.** Verify the first-use package download against the committed checksum and cache the complete
  package. Later use of the cached package needs no download.
- **What you do.** Arrange package access or transfer the complete matching platform package and verify it against the
  pin. Copying only its launcher omits the runtime. See [air-gapped machines](wrapper.md#air-gapped-machines).

### A checksum mismatch refuses the download

- **Symptom.** The downloaded package differs from the committed checksum.
- **Why.** The download, selected platform package or pin may be wrong or changed.
- **Cook's responsibility.** Refuse execution of a mismatched download and do not accept it as the cached package.
- **What you do.** Compare the reviewed pin and package source and investigate the discrepancy. Do not silently change
  a checksum merely to make a download run. See [wrapper security](wrapper.md#security).

### Competing pins run different versions

- **Symptom.** A task-runner entry point and the wrapper select different engine versions.
- **Why.** Separate pins can change independently. Changing a version without matching package checksums also leaves
  an inconsistent pin.
- **Cook's responsibility.** The intended wrapper uses the committed version and platform-package checksums, reuses its
  cached package and changes only through an explicit upgrade.
- **What you do.** Review version and checksum changes together and avoid competing engine pins. Select recipe versions
  separately. See [one pin](wrapper.md#one-pin) and [upgrading](wrapper.md#upgrading).
