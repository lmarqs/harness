# Native program hooks and recipe boundaries

## Objective

**Problem.** Coding agents and other programs can run formatters, checks or notifications at their own events, but
hook formats, event payloads and failure behavior differ. A copied hook can fail on another machine or run with more
authority than its reviewer expected.

**Goal.** Useful hook configuration remains inspectable in the program's native files, with its dependencies and
runtime consequences explained.

## Native hook configuration

An [ingredient](ingredients.md) models the configuration a program consumes, including native hook settings where
applicable. A trusted Groovy [recipe](recipes.md) can declare file operations that write those settings and supporting
scripts. Cook reviews and writes the declared files; the configured program runs the hooks.

This follows [the architecture](../architecture.md): external command execution is not a batch action. Writing a
hook command does not execute it as part of the batch. Cook's recovery patches concern declared file changes, not later
effects of a hook.

## Useful hook choices

Keep program-specific guidance with its cookbook's model and recipes. Useful configurations can include:

| Purpose | What to review |
| --- | --- |
| Format or lint an edited file | Supported event, file-path handling, command dependencies and cost per edit |
| Check related repository files | Which changes trigger the check and what a failed check does to the session |
| Guard a path before a tool runs | Whether the native event can actually block the operation and what paths it sees |
| Notify a person | Supported event, destination, credentials, message content and failure behavior |

These are configuration purposes. Do not assume every program supports every purpose or treats failures identically.
Document the native program's behavior and any missing support.

Keep frequent feedback fast and focused; use the repository's external check runner for broader validation. A hook
that fixes formatting should avoid repeatedly rewriting unchanged content. Hook feedback does not prove that a change
passes the repository's checks. See [repository checks and configuration](verify.md).

## Existing hooks and runtime review

Choose targets deliberately when a file already contains hooks from a person or plugin. Key or section operations
preserve content outside their declared targets; a whole-file operation affects the entire file. Review the final
combined diff, including any replacement of an existing notification command or event entry.

Review scripts alongside the settings that call them. Establish their working directory, executable requirements,
path quoting, runtime permissions and behavior when a dependency is missing. Project trust and hook enablement are
properties of the program.

Keep credentials out of committed hook configuration. When the chosen program or script supports environment-based
credentials, document that prerequisite. Treat event payloads and agent-written messages as untrusted input; do not
turn their contents into shell commands or instructions for another agent. Notifications can also disclose repository
content. See [safety guidelines](safety.md) and [program permissions](policy.md).

Applying a new version of a recipe removes hook contributions its previous version owned and the new version no
longer declares. [Revert](revert.md#revert) applies a recorded batch's reverse patches. Neither reverses actions a hook
has already performed.

## Deferred boundaries

Recipe lifecycle hooks, decorators, annotations and injection APIs require a separate ingredient/recipe model
discussion.
