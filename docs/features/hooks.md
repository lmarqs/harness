# Native program hooks and recipe boundaries

## Objective

**Problem.** Coding agents and other programs can run formatters, checks or notifications at their own events, but
hook formats, event payloads and failure behaviour differ. A copied hook can fail on another machine or run with more
authority than its reviewer expected.

**Goal.** Useful hook configuration remains inspectable in the program's native files, with its dependencies and
runtime consequences explained.

## Native hook configuration

An [ingredient](ingredients.md) models the configuration a program consumes, including native hook settings where
applicable. A trusted Groovy [recipe](recipes.md) can declare file operations that write those settings and supporting
scripts. Cook reviews and writes the declared files; the configured program runs the hooks.

This follows [the architecture](../architecture.md): external command execution is not a batch action. Writing a
hook command does not execute it as part of the batch, and approving its configuration does not make Cook the hook
runner. Cook's recovery patches concern declared file changes, not later effects of a hook.

There is no required `.cook/hooks.yaml`, neutral event vocabulary, payload-normalisation contract, `the-cook hook`
command or lifecycle-action dispatcher. Cook does not assign session roles, inject standing context or schedule
notifications around a fixed development lifecycle.

## Useful hook choices

Keep program-specific guidance with its cookbook's model and recipes. Useful configurations can include:

| Purpose | What to review |
| --- | --- |
| Format or lint an edited file | Supported event, file-path handling, command dependencies and cost per edit |
| Check related repository files | Which changes trigger the check and what a failed check does to the session |
| Guard a path before a tool runs | Whether the native event can actually block the operation and what paths it sees |
| Notify a person | Supported event, destination, credentials, message content and failure behaviour |

These are configuration purposes, not Cook event names or an API. Do not assume every program supports every purpose
or treats failures identically. Document the native program's behaviour and any missing support.

Keep frequent feedback fast and focused; use the repository's external check runner for broader validation. A hook
that fixes formatting should avoid repeatedly rewriting unchanged content. Hook feedback does not prove that a change
passes the repository's checks. See [repository checks and configuration](verify.md).

## Existing hooks and runtime review

Choose targets deliberately when a file already contains hooks from a person or plugin. Key or section operations
preserve content outside their declared targets; a whole-file operation affects the entire file. Review the final
combined diff, including any replacement of an existing notification command or event entry. There is no adoption
step or ownership gate for existing settings.

Review scripts alongside the settings that call them. Establish their working directory, executable requirements,
path quoting, runtime permissions and behaviour when a dependency is missing. Project trust and hook enablement are
properties of the program, not guarantees supplied by Cook.

Keep credentials out of committed hook configuration. When the chosen program or script supports environment-based
credentials, document that prerequisite. Treat event payloads and agent-written messages as untrusted input; do not
turn their contents into shell commands or instructions for another agent. Notifications can also disclose repository
content. See [safety guidelines](safety.md) and [program permissions](policy.md).

[Discard](remove.md#discard) deletes the supplied recipe's declared hook contributions. [Revert](remove.md#revert)
restores a recorded operation's prior values using its history. Neither reverses actions a hook has already performed.

## Deferred boundaries

Recipe lifecycle hooks, decorators, annotations and injection APIs require a separate ingredient/recipe model
discussion. Native program configuration does not select any of those APIs.

Agent-session orchestration is future work tracked in [issue #6](https://github.com/lmarqs/the-cook/issues/6). This page
does not design session events, prompt delivery, notification protocols or a lifecycle runner.
